package app.okgram.okhttp.builder;


import app.okgram.okhttp.OKHttpUtils;
import app.okgram.okhttp.request.OtherRequest;
import app.okgram.okhttp.request.RequestCall;

public class HeadBuilder extends GetBuilder {
    @Override
    public RequestCall build() {
        return new OtherRequest(null, null, OKHttpUtils.METHOD.HEAD, url, tag, params, headers, id).build();
    }
}
