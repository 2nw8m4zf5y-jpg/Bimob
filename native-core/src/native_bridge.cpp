#include <jni.h>
#include "bimob/native_session.hpp"
#include <string>
namespace {
std::string fromJString(JNIEnv*e,jstring s){if(!s)return {};const char*c=e->GetStringUTFChars(s,nullptr);if(!c)return {};std::string v(c);e->ReleaseStringUTFChars(s,c);return v;}
jstring toJString(JNIEnv*e,const std::string&s){return e->NewStringUTF(s.c_str());}
}
extern "C" JNIEXPORT jlong JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeCreateSession(JNIEnv*,jobject){try{return static_cast<jlong>(bimob::bridge::createSession());}catch(...){return 0;}}
extern "C" JNIEXPORT void JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeDestroySession(JNIEnv*,jobject,jlong h){bimob::bridge::destroySession(static_cast<std::uint64_t>(h));}
extern "C" JNIEXPORT jstring JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeDispatchCommandJson(JNIEnv*e,jobject,jlong h,jstring json){return toJString(e,bimob::bridge::dispatchCommandJson(static_cast<std::uint64_t>(h),fromJString(e,json)));}
extern "C" JNIEXPORT jstring JNICALL Java_com_bimob_app_core_nativebridge_NativeEngineBridge_nativeExportCanonicalSnapshot(JNIEnv*e,jobject,jlong h){return toJString(e,bimob::bridge::exportCanonicalSnapshot(static_cast<std::uint64_t>(h)));}
