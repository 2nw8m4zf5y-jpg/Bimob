#include "bimob/json_value.hpp"
#include <cassert>
#include <string>
using bimob::json::parse;
using bimob::json::ParseError;
template<class F>void rejects(F f){bool threw=false;try{f();}catch(const ParseError&){threw=true;}assert(threw);}
int main(){
 auto v=parse(R"({"text":"escaped \" quote \\ and \uD83D\uDE03","n":-1.25e2,"a":[true,false,null]})");
 assert(v.object());assert(v.get("n")&&*v.get("n")->number()==-125.0);assert(v.get("a")->array()->size()==3);
 assert(v.get("text")->string()->find("\xF0\x9F\x98\x83")!=std::string::npos);
 rejects([]{parse(R"({"x":1,"x":2})");});
 auto numeric=parse(R"([0,-0,1.5,-2.75,6.02e23,1e+3,4E-2])");
 const auto* nums=numeric.array();assert(nums&&nums->size()==7);
 assert(*(*nums)[0].number()==0.0);assert(*(*nums)[1].number()==0.0);
 assert(*(*nums)[2].number()==1.5);assert(*(*nums)[3].number()==-2.75);
 assert(*(*nums)[5].number()==1000.0);assert(*(*nums)[6].number()==0.04);
 auto invalidUtf8=[](std::initializer_list<unsigned char> bytes){std::string input=R"({"x":")";for(auto byte:bytes)input.push_back(static_cast<char>(byte));input+="\"}";return input;};
 rejects([&]{parse(invalidUtf8({0x80}));});
 rejects([&]{parse(invalidUtf8({0xc0,0xaf}));});
 rejects([&]{parse(invalidUtf8({0xed,0xa0,0x80}));});
 rejects([&]{parse(invalidUtf8({0xf4,0x90,0x80,0x80}));});
 rejects([&]{parse(invalidUtf8({0xe2,0x82}));});
 rejects([]{parse(R"({"x":01})");});
 rejects([]{parse(R"({"x":NaN})");});
 rejects([]{parse(R"({"x":1e9999})");});
 rejects([]{parse(R"({"x":-1e9999})");});
 rejects([]{parse(R"({"x":"\uD800"})");});
 rejects([]{parse(R"({"x":1} trailing)");});
 rejects([]{parse(R"([1,])");});
 rejects([]{parse(std::string(1024*1024+1,' '));});
 std::string nested(70,'[');nested+="0";nested+=std::string(70,']');rejects([&]{parse(nested);});
 return 0;
}
