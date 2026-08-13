package com.smartfitagent;

import com.smartfitagent.ai.AiClientFactory;
import com.smartfitagent.agent.AgentFactory;
import com.smartfitagent.db.Database;
import com.smartfitagent.db.DatabaseFactory;
import com.smartfitagent.event.LearningEventBus;
import com.smartfitagent.mvc.ApiController;
import com.smartfitagent.mvc.Router;
import com.smartfitagent.mvc.StaticController;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Path;

public class App {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("APP_PORT", System.getenv().getOrDefault("APP_PORT", "8080")));
        Database database = DatabaseFactory.create();
        database.init();
        LearningEventBus eventBus = new LearningEventBus();
        StudyService service = new StudyService(database, eventBus);
        AgentFactory agentFactory = new AgentFactory(AiClientFactory.create(), service);
        ApiController api = new ApiController(service, agentFactory);
        StaticController statics = new StaticController(Path.of("public"));
        Router router = new Router();
        router.get("/health", api::health);
        router.get("/api/dashboard", api::dashboard);
        router.get("/api/courses", api::courses);
        router.get("/api/plans", api::plans);
        router.get("/api/notes", api::notes);
        router.get("/api/quiz", api::quiz);
        router.get("/api/analytics", api::analytics);
        router.post("/api/seed", api::seed);
        router.post("/api/user", api::saveUser);
        router.post("/api/plans", api::savePlan);
        router.post("/api/notes", api::saveNote);
        router.post("/api/quiz/submit", api::saveAttempt);
        router.post("/api/agent/chat", api::chat);
        router.fallback(statics::serve);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", router);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("AI健身教练网站已启动: http://127.0.0.1:" + port);
        System.out.println("数据库模式: " + database.name());
        System.out.println("AI模式: " + AiClientFactory.create().name());
    }
}
