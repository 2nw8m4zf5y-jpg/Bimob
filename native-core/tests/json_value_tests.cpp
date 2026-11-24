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
