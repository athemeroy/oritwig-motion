#include "rlottie.h"
#include <fstream>
#include <sstream>
#include <iostream>
#include <vector>
#include <cassert>
int main(int argc,char**argv) {
 assert(argc==2);std::ifstream in(argv[1]);std::stringstream s;s<<in.rdbuf();auto a=rlottie::Animation::loadFromData(s.str(),"",nullptr);assert(a);
 size_t w,h;a->size(w,h);assert(w==64&&h==64&&a->totalFrame()==60&&a->frameRate()==30);
 std::vector<uint32_t> pixels(w*h);rlottie::Surface surface(pixels.data(),w,h,w*4);bool ok=false;
 a->renderSync(0,surface,true,&ok);assert(ok);uint32_t p=pixels[32*64+16];std::cout<<"frame0 center="<<std::hex<<p<<std::dec<<"\n";assert((p>>24)==255);assert(pixels[0]==0);auto first=pixels;
 a->renderSync(59,surface,true,&ok);assert(ok);assert(first!=pixels);assert(pixels[32*64+16]==0);assert(pixels[32*64+48]==p);
 std::cout<<"PASS unchanged rlottie load, metadata, frame rasterization, transparency, movement\n";
}
