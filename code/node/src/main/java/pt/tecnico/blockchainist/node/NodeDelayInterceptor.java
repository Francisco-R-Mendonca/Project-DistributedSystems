package pt.tecnico.blockchainist.node;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

public class NodeDelayInterceptor implements ServerInterceptor {
    public static final Context.Key<Integer> DELAY_KEY = Context.key("delaySeconds");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String delayHeader = headers.get(Metadata.Key.of("delaySeconds", Metadata.ASCII_STRING_MARSHALLER));
        int delay = 0;
        if (delayHeader != null) {
            try {
                delay = Integer.parseInt(delayHeader);
            } catch (NumberFormatException ignored) {}
        }

        Context context = Context.current().withValue(DELAY_KEY, delay);
        return Contexts.interceptCall(context, call, headers, next);
    } 
}
