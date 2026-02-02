package one.chandan.rubato.subsonic.utils;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;
import one.chandan.rubato.util.ServerStatus;

public class ServerReachabilityInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        try {
            Response response = chain.proceed(chain.request());
            ServerStatus.markReachable();
            return response;
        } catch (IOException exception) {
            ServerStatus.markUnreachable();
            throw exception;
        }
    }
}
