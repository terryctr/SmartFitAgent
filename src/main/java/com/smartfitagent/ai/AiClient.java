package com.smartfitagent.ai;
public interface AiClient { String name(); String complete(String systemPrompt,String userPrompt) throws Exception; }
