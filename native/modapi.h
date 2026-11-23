#pragma once
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
#define BBMOD_API_VERSION 1

typedef struct BBModContext {
    uint32_t    api_version;
    void*       il2cpp_handle;  /* dlopen handle of libil2cpp.so: dlsym(handle, "il2cpp_...") */
    void*       il2cpp_domain;  /* Il2CppDomain*; the loader thread is already attached */
    uintptr_t   il2cpp_base;    /* load base of libil2cpp.so: base + RVA (from dump.cs) = function address */
    const char* mods_dir;
    void      (*log)(const char* msg);
} BBModContext;

/* Every mod exports:  int mod_init(const BBModContext* ctx);   return 0 on success */
typedef int (*BBModInit)(const BBModContext*);
#ifdef __cplusplus
}
#endif
