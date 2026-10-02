// Oritwig JNI/platform adapter, GPL-3.0-or-later. Rendering remains in unchanged rlottie.
#include <jni.h>
#include <android/bitmap.h>
#include <cmath>
#include <memory>
#include <string>
#include "rlottie.h"
static void fail(JNIEnv* e,const char* type,const char* message) { e->ThrowNew(e->FindClass(type),message); }
extern "C" JNIEXPORT jlong JNICALL Java_dev_oritwig_motion_engine_MotionAnimation_nativeOpen(JNIEnv* e,jclass,jbyteArray data) {
 if(!data || e->GetArrayLength(data)<2 || e->GetArrayLength(data)>2097152) { fail(e,"java/lang/IllegalArgumentException","JSON byte limit exceeded"); return 0; }
 std::string json(e->GetArrayLength(data),'\0'); e->GetByteArrayRegion(data,0,json.size(),reinterpret_cast<jbyte*>(&json[0]));
 if(e->ExceptionCheck()) return 0;
 try {
  auto a=rlottie::Animation::loadFromData(std::move(json),"",nullptr);
  if(!a) { fail(e,"java/lang/IllegalArgumentException","rlottie could not parse this animation"); return 0; }
  size_t w=0,h=0; a->size(w,h);
  if(!w||!h||w>4096||h>4096||!a->totalFrame()||a->totalFrame()>1800||!std::isfinite(a->frameRate())||a->frameRate()<1||a->frameRate()>120) { fail(e,"java/lang/IllegalArgumentException","Animation metadata exceeds limits"); return 0; }
  return reinterpret_cast<jlong>(a.release());
 } catch(const std::exception& x) { fail(e,"java/lang/IllegalArgumentException",x.what()); return 0; }
}
extern "C" JNIEXPORT jdoubleArray JNICALL Java_dev_oritwig_motion_engine_MotionAnimation_nativeInfo(JNIEnv* e,jclass,jlong p) {
 auto a=reinterpret_cast<rlottie::Animation*>(p); if(!a) return nullptr;
 size_t w,h;a->size(w,h); jdouble values[]={static_cast<double>(w),static_cast<double>(h),static_cast<double>(a->totalFrame()),a->frameRate()};
 auto out=e->NewDoubleArray(4); if(out)e->SetDoubleArrayRegion(out,0,4,values);return out;
}
extern "C" JNIEXPORT void JNICALL Java_dev_oritwig_motion_engine_MotionAnimation_nativeRender(JNIEnv* e,jclass,jlong p,jint f,jobject bitmap) {
 auto a=reinterpret_cast<rlottie::Animation*>(p); AndroidBitmapInfo b{};
 if(!a||f<0||static_cast<size_t>(f)>=a->totalFrame()||!bitmap||AndroidBitmap_getInfo(e,bitmap,&b)!=0||b.format!=ANDROID_BITMAP_FORMAT_RGBA_8888||!b.width||!b.height||b.width>1024||b.height>1024||b.stride<b.width*4) { fail(e,"java/lang/IllegalArgumentException","Invalid frame or render surface");return; }
 void* pixels=nullptr; if(AndroidBitmap_lockPixels(e,bitmap,&pixels)!=0) { fail(e,"java/lang/IllegalStateException","Could not lock render surface");return; }
 bool ok=false;
 try { rlottie::Surface surface(static_cast<uint32_t*>(pixels),b.width,b.height,b.stride); a->renderSync(f,surface,true,&ok); }
 catch(...) { AndroidBitmap_unlockPixels(e,bitmap);fail(e,"java/lang/IllegalStateException","Native render failed");return; }
 AndroidBitmap_unlockPixels(e,bitmap);
 if(!ok) fail(e,"java/lang/IllegalStateException","Native render did not complete");
}
extern "C" JNIEXPORT void JNICALL Java_dev_oritwig_motion_engine_MotionAnimation_nativeClose(JNIEnv*,jclass,jlong p) { delete reinterpret_cast<rlottie::Animation*>(p); }
