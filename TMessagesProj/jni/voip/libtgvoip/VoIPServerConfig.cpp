//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "VoIPServerConfig.h"
#include <stdlib.h>
#include "logging.h"
#include <sstream>
#include <locale>

using namespace tgvoip;

ServerConfig* ServerConfig::sharedInstance=NULL;

ServerConfig::ServerConfig(){
}

ServerConfig::~ServerConfig(){
}

ServerConfig *ServerConfig::GetSharedInstance(){
	if(!sharedInstance)
		sharedInstance=new ServerConfig();
	return sharedInstance;
}

bool ServerConfig::GetBoolean(std::string name, bool fallback){
	MutexGuard sync(mutex);
	if(ContainsKey(name) && config[name].is_bool())
		return config[name].bool_value();
	return fallback;
}

double ServerConfig::GetDouble(std::string name, double fallback){
	MutexGuard sync(mutex);
	if(ContainsKey(name) && config[name].is_number())
		return config[name].number_value();
	return fallback;
}

int32_t ServerConfig::GetInt(std::string name, int32_t fallback){
	MutexGuard sync(mutex);
	if(ContainsKey(name) && config[name].is_number())
		return config[name].int_value();
	return fallback;
}

std::string ServerConfig::GetString(std::string name, std::string fallback){
	MutexGuard sync(mutex);
	if(ContainsKey(name) && config[name].is_string())
		return config[name].string_value();
	return fallback;
}

void ServerConfig::Update(std::string jsonString){
	MutexGuard sync(mutex);
	LOGD("=== Updating voip config ===");
	LOGD("%s", jsonString.c_str());
	std::string jsonError;
	config=json11::Json::parse(jsonString, jsonError);
	if(!jsonError.empty())
		LOGE("Error parsing server config: %s", jsonError.c_str());
}


bool ServerConfig::ContainsKey(std::string key){
	return config.object_items().find(key)!=config.object_items().end();
}
