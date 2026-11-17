package com.tikkle.support;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테스트에서 업비트 API 를 대신하는 가짜 HTTP 서버입니다.
 * 테스트가 응답(상태 코드·본문·지연)을 순서대로 넣어 두면 들어온 요청에 그 순서대로 돌려줍니다.
 * 요청의 메서드와 경로별로 응답을 정해 둘 수도 있습니다.
 * 실제 HTTP 요청이 오가므로 타임아웃과 오류 응답 처리까지 운영과 같은 경로로 검증할 수 있습니다.
 *
 * 서버는 테스트 JVM 에서 한 번만 띄웁니다. 주소가 바뀌지 않아야 스프링 컨텍스트를 재사용할 수 있기 때문입니다.
 */
public final class UpbitMockServer {

    private static final int NO_STUB_STATUS = 500;
    private static final String NO_STUB_BODY = "{\"error\":{\"name\":\"no_stub\",\"message\":\"준비된 응답이 없습니다.\"}}";

    private static final MockWebServer SERVER = new MockWebServer();
    private static final StubDispatcher DISPATCHER = new StubDispatcher();
    private static final AtomicInteger REQUEST_COUNT_BEFORE_FIRST_TEST = new AtomicInteger(-1);

    static {
        SERVER.setDispatcher(DISPATCHER);
        try {
            SERVER.start();
        } catch (IOException e) {
            throw new UncheckedIOException("업비트 대역 서버를 시작하지 못했습니다.", e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(UpbitMockServer::shutdown));
    }

    private UpbitMockServer() {
    }

    public static String baseUrl() {
        return "http://127.0.0.1:" + SERVER.getPort();
    }

    /**
     * 다음 요청에 돌려줄 JSON 응답을 넣습니다.
     */
    public static void enqueueJson(int status, String body) {
        DISPATCHER.responses.add(jsonResponse(status, body));
    }

    /**
     * 응답 헤더를 지연시켜 돌려줄 JSON 응답을 넣습니다. 클라이언트의 읽기 타임아웃을 재현할 때 씁니다.
     */
    public static void enqueueJson(int status, String body, Duration delay) {
        DISPATCHER.responses.add(jsonResponse(status, body).setHeadersDelay(delay.toMillis(), TimeUnit.MILLISECONDS));
    }

    /**
     * 메서드와 경로가 같은 요청에 돌려줄 JSON 응답을 정합니다. 경로는 쿼리 문자열까지 포함해 적습니다.
     * 같은 요청에 여러 번 정하면 정한 순서대로 돌려주고, 마지막 응답은 되풀이합니다.
     * 운영 코드가 업비트를 부르는 순서와 무관하게 응답을 정할 때 씁니다.
     * 여기서 정한 응답은 {@link #enqueueJson(int, String)} 으로 넣은 응답보다 먼저 쓰입니다.
     */
    public static void stubJson(String method, String path, int status, String body) {
        Deque<MockResponse> routed = DISPATCHER.routes.computeIfAbsent(routeKey(method, path), key -> new ArrayDeque<>());
        synchronized (routed) {
            routed.add(jsonResponse(status, body));
        }
    }

    /**
     * 마지막 {@link #reset()} 이후 받은 요청을 받은 순서대로 돌려줍니다.
     */
    public static List<RecordedRequest> receivedRequests() {
        return List.copyOf(DISPATCHER.received);
    }

    /**
     * 첫 테스트가 시작되기 전에(서버 기동 중에) 받은 요청 수입니다. 첫 {@link #reset()} 전에는 -1 입니다.
     */
    public static int requestCountBeforeFirstTest() {
        return REQUEST_COUNT_BEFORE_FIRST_TEST.get();
    }

    /**
     * 준비해 둔 응답과 받은 요청 기록을 비웁니다. 테스트마다 시작 전에 호출합니다.
     */
    public static void reset() {
        REQUEST_COUNT_BEFORE_FIRST_TEST.compareAndSet(-1, DISPATCHER.received.size());
        DISPATCHER.responses.clear();
        DISPATCHER.routes.clear();
        DISPATCHER.received.clear();
    }

    private static String routeKey(String method, String path) {
        return method + " " + path;
    }

    private static MockResponse jsonResponse(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static void shutdown() {
        try {
            SERVER.shutdown();
        } catch (IOException ignored) {
            // JVM 종료 중이라 무시한다
        }
    }

    private static final class StubDispatcher extends Dispatcher {

        private final Queue<MockResponse> responses = new ConcurrentLinkedQueue<>();
        private final Map<String, Deque<MockResponse>> routes = new ConcurrentHashMap<>();
        private final List<RecordedRequest> received = new CopyOnWriteArrayList<>();

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            received.add(request);
            MockResponse routed = nextRouted(request);
            if (routed != null) {
                return routed;
            }
            MockResponse next = responses.poll();
            // 준비한 응답이 없으면 500 을 돌려줘 테스트가 조용히 통과하지 않게 한다
            return next != null ? next : jsonResponse(NO_STUB_STATUS, NO_STUB_BODY);
        }

        private MockResponse nextRouted(RecordedRequest request) {
            Deque<MockResponse> routed = routes.get(routeKey(request.getMethod(), request.getPath()));
            if (routed == null) {
                return null;
            }
            synchronized (routed) {
                // 마지막 응답은 꺼내지 않고 남겨 둬 같은 요청이 더 와도 되풀이한다
                MockResponse next = routed.size() > 1 ? routed.poll() : routed.peek();
                return next != null ? next.clone() : null;
            }
        }
    }
}
