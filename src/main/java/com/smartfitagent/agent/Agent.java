package com.smartfitagent.agent;
import com.smartfitagent.model.*;
public interface Agent { AgentReply reply(String message, UserProfile user) throws Exception; }
