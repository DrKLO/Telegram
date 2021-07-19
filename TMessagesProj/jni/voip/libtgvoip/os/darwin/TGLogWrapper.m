#import <Foundation/Foundation.h>

void (*TGVoipLoggingFunction)(NSString *) = NULL;

void __tgvoip_call_tglog(const char* format, ...){
	va_list args;
	va_start(args, format);
    NSString *string = [[NSString alloc] initWithFormat:[[NSString alloc]initWithUTF8String:format] arguments:args];
	va_end(args);
    if (TGVoipLoggingFunction) {
        TGVoipLoggingFunction(string);
    }
}
