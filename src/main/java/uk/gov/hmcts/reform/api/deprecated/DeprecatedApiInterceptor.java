package uk.gov.hmcts.reform.api.deprecated;

import java.lang.reflect.GenericDeclaration;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.springframework.http.HttpHeaders.WARNING;


public class DeprecatedApiInterceptor extends HandlerInterceptorAdapter {
    private static final Logger log = LoggerFactory.getLogger(DeprecatedApiInterceptor.class);
    protected static final String DEPRECATED_AND_REMOVED = " is deprecated and will be removed by ";
    private Map<GenericDeclaration, Map<String, String>> responseHeadersByHandler = new IdentityHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod) {
            handleHeaders(response, (HandlerMethod) handler);
        }
        return true;
    }

    private void handleHeaders(HttpServletResponse response, HandlerMethod handler) {
        for (GenericDeclaration source : getAnnotatedSources(handler)) {
            boolean handled = handleSource(response, source);
            if (handled) {
                return;
            }
        }

        log.trace("No @APIDeprecated headers found for request handler: {}", handler);
    }

    private static List<GenericDeclaration> getAnnotatedSources(HandlerMethod handler) {
        return Arrays.asList(handler.getBeanType(), handler.getMethod());
    }

    private boolean handleSource(HttpServletResponse response, GenericDeclaration source) {
        Map<String, String> headers = responseHeadersByHandler
            .computeIfAbsent(source, DeprecatedApiInterceptor::computeHeaders);

        copyWarning(response, headers);

        return !headers.isEmpty();
    }

    private static Map<String, String> computeHeaders(GenericDeclaration source) {
        Map<String, String> headers = new HashMap<>();
        if (source.isAnnotationPresent(APIDeprecated.class)) {
            APIDeprecated deprecated = source.getAnnotation(APIDeprecated.class);
            RequestMapping mapping = source.getAnnotation(RequestMapping.class);

            headers.put(WARNING, getWarningMessage(deprecated, mapping));
        }
        return headers;
    }

    private static String getWarningMessage(APIDeprecated apiDeprecated, RequestMapping requestMapping) {
        StringBuilder sb = new StringBuilder();
        sb.append("The ")
            .append(requestMapping != null && requestMapping.value().length > 0 ? requestMapping.value()[0] : "")
            .append(" ")
            .append(apiDeprecated.name()).append(DEPRECATED_AND_REMOVED)
            .append(apiDeprecated.expireDate())
            .append(". Please see ")
            .append(apiDeprecated.docLink())
            .append(" for details. ")
            .append(apiDeprecated.note());
        return sb.toString();
    }

    private static void copyWarning(HttpServletResponse response, Map<String, String> headers) {
        if (headers.get(WARNING) != null) {
            response.setHeader(WARNING, headers.get(WARNING));
        }
    }
}
