// Example mod. Build:  $CXX -shared -fPIC -O2 -std=c++17 -I../../native -o hello.so hello.cpp
// Looks up the game's PlayerManager class through the IL2CPP API (no offsets needed) and logs it.
#include <dlfcn.h>
#include <stdio.h>
#include "modapi.h"

typedef void*  (*get_asms_t)(void*, size_t*);
typedef void*  (*asm_image_t)(void*);
typedef void*  (*class_from_name_t)(void*, const char*, const char*);
typedef const char* (*class_name_t)(void*);

extern "C" int mod_init(const BBModContext* c) {
    auto get_asms = (void**(*)(void*, size_t*))dlsym(c->il2cpp_handle, "il2cpp_domain_get_assemblies");
    auto asm_image = (asm_image_t)dlsym(c->il2cpp_handle, "il2cpp_assembly_get_image");
    auto cfn = (class_from_name_t)dlsym(c->il2cpp_handle, "il2cpp_class_from_name");
    auto cname = (class_name_t)dlsym(c->il2cpp_handle, "il2cpp_class_get_name");
    size_t n = 0; void** as = get_asms(c->il2cpp_domain, &n);
    for (size_t i = 0; i < n; i++) {
        void* k = cfn(asm_image(as[i]), "", "PlayerManager");
        if (k) { char m[128]; snprintf(m, sizeof m, "found class %s (il2cpp base 0x%lx)", cname(k), (unsigned long)c->il2cpp_base); c->log(m); return 0; }
    }
    c->log("PlayerManager not found");
    return 1;
}
