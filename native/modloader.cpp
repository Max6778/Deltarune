// libmodloader.so: waits for IL2CPP + Assembly-CSharp, then dlopens every mod .so and calls mod_init().
#include <jni.h>
#include <dlfcn.h>
#include <link.h>
#include <dirent.h>
#include <pthread.h>
#include <unistd.h>
#include <sys/stat.h>
#include <stdio.h>
#include <string.h>
#include <stdarg.h>
#include <atomic>
#include <string>
#include <vector>
#include <algorithm>
#include <android/log.h>
#include "modapi.h"

#if defined(__aarch64__)
#define OTHER_ABI "armeabi-v7a"
#else
#define OTHER_ABI "arm64-v8a"
#endif

static std::string g_mods, g_internal, g_logPath;

static void logf(const char* fmt, ...) {
    char buf[1024];
    va_list ap; va_start(ap, fmt); vsnprintf(buf, sizeof buf, fmt, ap); va_end(ap);
    __android_log_print(ANDROID_LOG_INFO, "BBModLoader", "%s", buf);
    if (FILE* f = fopen(g_logPath.c_str(), "a")) { fprintf(f, "%s\n", buf); fclose(f); }
}
static void mod_log(const char* msg) { logf("[mod] %s", msg ? msg : "(null)"); }

static bool ends_with(const std::string& s, const char* suf) {
    size_t n = strlen(suf);
    return s.size() >= n && s.compare(s.size() - n, n, suf) == 0;
}

static uintptr_t find_base(const char* lib) {
    struct Ctx { const char* lib; uintptr_t base; } c{lib, 0};
    dl_iterate_phdr([](struct dl_phdr_info* i, size_t, void* d) -> int {
        Ctx* c = (Ctx*)d;
        if (i->dlpi_name && ends_with(i->dlpi_name, c->lib)) { c->base = i->dlpi_addr; return 1; }
        return 0;
    }, &c);
    return c.base;
}

static bool copy_file(const std::string& from, const std::string& to) {
    FILE* in = fopen(from.c_str(), "rb"); if (!in) return false;
    chmod(to.c_str(), 0644); // allow overwrite of an earlier read-only copy
    FILE* out = fopen(to.c_str(), "wb"); if (!out) { fclose(in); return false; }
    char buf[65536]; size_t n;
    while ((n = fread(buf, 1, sizeof buf, in)) > 0) fwrite(buf, 1, n, out);
    fclose(in); fclose(out);
    chmod(to.c_str(), 0444);
    return true;
}

static void collect(const std::string& dir, std::vector<std::string>& out, int depth) {
    DIR* d = opendir(dir.c_str()); if (!d) return;
    while (dirent* e = readdir(d)) {
        std::string n = e->d_name;
        if (n == "." || n == "..") continue;
        std::string p = dir + "/" + n;
        struct stat st;
        if (stat(p.c_str(), &st) != 0) continue;
        if (S_ISDIR(st.st_mode)) { if (depth < 1) collect(p, out, depth + 1); }
        else if (ends_with(n, ".so") && n.find(OTHER_ABI) == std::string::npos) out.push_back(p);
    }
    closedir(d);
}

static void load_mods(const BBModContext& ctx) {
    std::string lib = g_internal + "/modlibs";
    mkdir(lib.c_str(), 0755);
    std::vector<std::string> files;
    collect(g_mods, files, 0);
    std::sort(files.begin(), files.end());
    logf("found %zu mod file(s) in %s", files.size(), g_mods.c_str());
    int idx = 0;
    for (const std::string& f : files) {
        std::string dst = lib + "/" + std::to_string(idx++) + "_" + f.substr(f.find_last_of('/') + 1);
        if (!copy_file(f, dst)) { logf("copy failed: %s", f.c_str()); continue; }
        void* h = dlopen(dst.c_str(), RTLD_NOW);
        if (!h) { logf("dlopen failed for %s: %s", f.c_str(), dlerror()); continue; }
        BBModInit init = (BBModInit)dlsym(h, "mod_init");
        if (!init) { logf("%s has no mod_init", f.c_str()); continue; }
        int r = init(&ctx);
        logf("%s -> mod_init returned %d", f.c_str(), r);
    }
}

static void* worker(void*) {
    void* h = nullptr;
    for (int i = 0; i < 1200 && !h; i++) { h = dlopen("libil2cpp.so", RTLD_NOW | RTLD_NOLOAD); if (!h) usleep(100000); }
    if (!h) { logf("libil2cpp.so never appeared"); return nullptr; }
    typedef void* (*domain_get_t)();
    typedef void* (*thread_attach_t)(void*);
    typedef void** (*get_assemblies_t)(void*, size_t*);
    typedef void* (*asm_image_t)(void*);
    typedef const char* (*image_name_t)(void*);
    auto domain_get = (domain_get_t)dlsym(h, "il2cpp_domain_get");
    auto thread_attach = (thread_attach_t)dlsym(h, "il2cpp_thread_attach");
    auto get_asms = (get_assemblies_t)dlsym(h, "il2cpp_domain_get_assemblies");
    auto asm_image = (asm_image_t)dlsym(h, "il2cpp_assembly_get_image");
    auto image_name = (image_name_t)dlsym(h, "il2cpp_image_get_name");
    if (!domain_get || !thread_attach || !get_asms || !asm_image || !image_name) { logf("il2cpp API symbols missing"); return nullptr; }

    void* dom = nullptr;
    for (int i = 0; i < 1200 && !dom; i++) { dom = domain_get(); if (!dom) usleep(100000); }
    if (!dom) { logf("il2cpp domain never became available"); return nullptr; }
    thread_attach(dom);

    bool ready = false;
    for (int i = 0; i < 600 && !ready; i++) {
        size_t n = 0; void** as = get_asms(dom, &n);
        for (size_t k = 0; k < n && !ready; k++) {
            const char* nm = image_name(asm_image(as[k]));
            if (nm && strcmp(nm, "Assembly-CSharp.dll") == 0) ready = true;
        }
        if (!ready) usleep(100000);
    }
    logf("il2cpp ready=%d, base=0x%lx", (int)ready, (unsigned long)find_base("libil2cpp.so"));
    BBModContext ctx;
    ctx.api_version = BBMOD_API_VERSION;
    ctx.il2cpp_handle = h;
    ctx.il2cpp_domain = dom;
    ctx.il2cpp_base = find_base("libil2cpp.so");
    ctx.mods_dir = g_mods.c_str();
    ctx.log = mod_log;
    load_mods(ctx);
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_baldimods_launcher_NativeLoader_start(JNIEnv* env, jclass, jstring jmods, jstring jint) {
    static std::atomic<bool> started(false);
    if (started.exchange(true)) return;
    const char* a = env->GetStringUTFChars(jmods, nullptr); g_mods = a; env->ReleaseStringUTFChars(jmods, a);
    const char* b = env->GetStringUTFChars(jint, nullptr);  g_internal = b; env->ReleaseStringUTFChars(jint, b);
    g_logPath = g_internal + "/modloader.log";
    remove(g_logPath.c_str());
    logf("modloader start, mods=%s", g_mods.c_str());
    pthread_t t; pthread_create(&t, nullptr, worker, nullptr); pthread_detach(t);
}
