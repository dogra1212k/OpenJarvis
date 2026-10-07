#ifdef __ANDROID__
#include <jni.h>
#include <android/bitmap.h>
#endif
#include <cstdint>
#include <algorithm>
#include <array>
#include <cmath>

namespace {
struct Point { float x, y; };
float segment(Point p, Point a, Point b) {
    float dx = b.x-a.x, dy = b.y-a.y;
    float t = std::clamp(((p.x-a.x)*dx+(p.y-a.y)*dy)/(dx*dx+dy*dy), 0.0f, 1.0f);
    return std::hypot(p.x-a.x-t*dx, p.y-a.y-t*dy);
}
float coverage(float distance, float width, float aa) {
    return std::clamp((width-distance)/aa+0.5f, 0.0f, 1.0f);
}
void blend(float &r, float &g, float &b, float nr, float ng, float nb, float alpha) {
    r += (nr-r)*alpha; g += (ng-g)*alpha; b += (nb-b)*alpha;
}
}

struct BitmapShape { uint32_t width, height, stride; };
// Deterministic native artwork: cyan J, circuit ring and a warm accent.
static void paintLogo(void *pixels, BitmapShape info) {
    std::array<Point, 27> path{};
    path[0] = {0.38f, 0.30f}; path[1] = {0.65f, 0.30f}; path[2] = {0.65f, 0.60f};
    for (int i=1; i<=24; ++i) {
        float t=i/24.0f, u=1-t;
        path[i+2] = {u*u*u*0.65f+3*u*u*t*0.65f+3*u*t*t*0.31f+t*t*t*0.29f,
                     u*u*u*0.60f+3*u*u*t*0.79f+3*u*t*t*0.83f+t*t*t*0.62f};
    }
    float aa=1.0f/std::min(info.width,info.height);
    for (uint32_t y=0; y<info.height; ++y) {
        auto *row=static_cast<uint8_t *>(pixels)+y*info.stride;
        for (uint32_t x=0; x<info.width; ++x) {
            Point p{(x+0.5f)/info.width,(y+0.5f)/info.height};
            float radius=std::hypot(p.x-0.5f,p.y-0.5f);
            float ambience=std::exp(-8*radius*radius);
            float r=5+5*ambience, g=13+13*ambience, b=24+19*ambience;
            float ring=coverage(std::abs(radius-0.386f),0.0025f,aa);
            blend(r,g,b,41,96,118,ring*0.75f);
            float circuit=std::min(segment(p,{0.13f,0.50f},{0.21f,0.50f}),
                                   segment(p,{0.79f,0.50f},{0.87f,0.50f}));
            blend(r,g,b,42,145,158,coverage(circuit,0.003f,aa));
            float d=1;
            for (size_t i=1;i<path.size();++i) d=std::min(d,segment(p,path[i-1],path[i]));
            float glow=std::exp(-1400*d*d)*0.33f;
            blend(r,g,b,20,133,157,glow);
            float fill=coverage(d,0.038f,aa);
            blend(r,g,b,90-60*p.y,244-38*p.y,255-32*p.y,fill);
            float dot=std::hypot(p.x-0.768f,p.y-0.242f);
            blend(r,g,b,252,184,75,coverage(dot,0.021f,aa));
            row[4*x]=static_cast<uint8_t>(std::clamp(r,0.0f,255.0f));
            row[4*x+1]=static_cast<uint8_t>(std::clamp(g,0.0f,255.0f));
            row[4*x+2]=static_cast<uint8_t>(std::clamp(b,0.0f,255.0f));
            row[4*x+3]=255;
        }
    }
}

#ifdef __ANDROID__
extern "C" JNIEXPORT void JNICALL
Java_com_dogra_hindijarvis_LogoRenderer_renderLogo(JNIEnv *env, jobject, jobject bitmap) {
    AndroidBitmapInfo info{};
    void *pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Logo bitmap could not open");
        return;
    }
    paintLogo(pixels, {info.width,info.height,info.stride});
    AndroidBitmap_unlockPixels(env, bitmap);
}
#endif

#ifdef JARVIS_RENDER_PREVIEW
#include <fstream>
#include <vector>
int main(int argc, char **argv) {
    if (argc != 2) return 1;
    const uint32_t size=512;
    std::vector<uint8_t> pixels(size*size*4);
    paintLogo(pixels.data(), {size,size,size*4});
    std::ofstream out(argv[1],std::ios::binary);
    out << "P6\n" << size << " " << size << "\n255\n";
    for(size_t i=0;i<pixels.size();i+=4) out.write(reinterpret_cast<char *>(pixels.data()+i),3);
    return out ? 0 : 1;
}
#endif
