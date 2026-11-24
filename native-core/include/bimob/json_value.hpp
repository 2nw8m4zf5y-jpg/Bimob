#pragma once
#include <charconv>
#include <cmath>
#include <cstdint>
#include <limits>
#include <map>
#include <string>
#include <string_view>
#include <variant>
#include <vector>

namespace bimob::json {
struct Value {
 using Array=std::vector<Value>;
 using Object=std::map<std::string,Value,std::less<>>;
 std::variant<std::nullptr_t,bool,double,std::string,Array,Object> data{nullptr};
 const Value* get(std::string_view key) const noexcept {
  const auto* o=std::get_if<Object>(&data); if(!o)return nullptr;
  auto i=o->find(key);return i==o->end()?nullptr:&i->second;
 }
 const std::string* string()const noexcept{return std::get_if<std::string>(&data);}
 const double* number()const noexcept{return std::get_if<double>(&data);}
 const bool* boolean()const noexcept{return std::get_if<bool>(&data);}
 const Array* array()const noexcept{return std::get_if<Array>(&data);}
 const Object* object()const noexcept{return std::get_if<Object>(&data);}
};
class ParseError final:public std::runtime_error { public: using std::runtime_error::runtime_error; };
class Parser {
 std::string_view input_;std::size_t pos_{0};std::size_t depth_{0};
 static constexpr std::size_t maxBytes=1024*1024,maxDepth=64;
 char peek()const noexcept{return pos_<input_.size()?input_[pos_]:'\0';}
 char take(){if(pos_>=input_.size())throw ParseError("Unexpected end of JSON");return input_[pos_++];}
 void ws(){while(peek()==' '||peek()=='\n'||peek()=='\r'||peek()=='\t')++pos_;}
 void expect(char c){ws();if(take()!=c)throw ParseError("Invalid JSON token");}
 static void appendUtf8(std::string&out,std::uint32_t cp){
  if(cp<=0x7f)out.push_back(static_cast<char>(cp));
  else if(cp<=0x7ff){out.push_back(static_cast<char>(0xc0|(cp>>6)));out.push_back(static_cast<char>(0x80|(cp&63)));}
  else if(cp<=0xffff){out.push_back(static_cast<char>(0xe0|(cp>>12)));out.push_back(static_cast<char>(0x80|((cp>>6)&63)));out.push_back(static_cast<char>(0x80|(cp&63)));}
  else{out.push_back(static_cast<char>(0xf0|(cp>>18)));out.push_back(static_cast<char>(0x80|((cp>>12)&63)));out.push_back(static_cast<char>(0x80|((cp>>6)&63)));out.push_back(static_cast<char>(0x80|(cp&63)));}
 }
 std::uint32_t hex4(){std::uint32_t v=0;for(int i=0;i<4;++i){char c=take();v<<=4;if(c>='0'&&c<='9')v|=c-'0';else if(c>='a'&&c<='f')v|=c-'a'+10;else if(c>='A'&&c<='F')v|=c-'A'+10;else throw ParseError("Invalid Unicode escape");}return v;}
 std::string str(){expect('"');std::string out;while(true){char c=take();if(c=='"')return out;if(static_cast<unsigned char>(c)<0x20)throw ParseError("Control character in JSON string");if(c!='\\'){out.push_back(c);continue;}char e=take();switch(e){case '"':case '\\':case '/':out.push_back(e);break;case 'b':out.push_back('\b');break;case 'f':out.push_back('\f');break;case 'n':out.push_back('\n');break;case 'r':out.push_back('\r');break;case 't':out.push_back('\t');break;case 'u':{auto cp=hex4();if(cp>=0xd800&&cp<=0xdbff){if(take()!='\\'||take()!='u')throw ParseError("Missing low surrogate");auto lo=hex4();if(lo<0xdc00||lo>0xdfff)throw ParseError("Invalid low surrogate");cp=0x10000+((cp-0xd800)<<10)+(lo-0xdc00);}else if(cp>=0xdc00&&cp<=0xdfff)throw ParseError("Unexpected low surrogate");appendUtf8(out,cp);break;}default:throw ParseError("Invalid string escape");}}
 }
 Value val(){ws();if(++depth_>maxDepth)throw ParseError("JSON nesting limit exceeded");Value v;char c=peek();if(c=='{'){take();Value::Object o;ws();if(peek()!='}')while(true){ws();if(peek()!='"')throw ParseError("Object key must be string");auto k=str();expect(':');auto item=val();if(!o.emplace(std::move(k),std::move(item)).second)throw ParseError("Duplicate JSON object key");ws();if(peek()=='}')break;expect(',');}expect('}');v.data=std::move(o);}
 else if(c=='['){take();Value::Array a;ws();if(peek()!=']')while(true){a.push_back(val());ws();if(peek()==']')break;expect(',');}expect(']');v.data=std::move(a);}
 else if(c=='"')v.data=str();
 else if(c=='t'){if(input_.substr(pos_,4)!="true")throw ParseError("Invalid literal");pos_+=4;v.data=true;}
 else if(c=='f'){if(input_.substr(pos_,5)!="false")throw ParseError("Invalid literal");pos_+=5;v.data=false;}
 else if(c=='n'){if(input_.substr(pos_,4)!="null")throw ParseError("Invalid literal");pos_+=4;v.data=nullptr;}
 else {std::size_t start=pos_;if(peek()=='-')++pos_;if(peek()=='0')++pos_;else{if(peek()<'1'||peek()>'9')throw ParseError("Invalid JSON number");while(peek()>='0'&&peek()<='9')++pos_;}if(peek()=='.'){++pos_;if(peek()<'0'||peek()>'9')throw ParseError("Invalid JSON fraction");while(peek()>='0'&&peek()<='9')++pos_;}if(peek()=='e'||peek()=='E'){++pos_;if(peek()=='+'||peek()=='-')++pos_;if(peek()<'0'||peek()>'9')throw ParseError("Invalid JSON exponent");while(peek()>='0'&&peek()<='9')++pos_;}double n{};auto r=std::from_chars(input_.data()+start,input_.data()+pos_,n);if(r.ec!=std::errc{}||r.ptr!=input_.data()+pos_||!std::isfinite(n))throw ParseError("JSON number out of range");v.data=n;}
 --depth_;return v;
 }
public:
 explicit Parser(std::string_view s):input_(s){if(s.size()>maxBytes)throw ParseError("JSON input size limit exceeded");}
 Value parse(){auto v=val();ws();if(pos_!=input_.size())throw ParseError("Trailing JSON content");return v;}
};
inline Value parse(std::string_view input){return Parser(input).parse();}
} // namespace bimob::json
