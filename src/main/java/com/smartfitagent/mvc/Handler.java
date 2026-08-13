package com.smartfitagent.mvc;
@FunctionalInterface public interface Handler { Response handle(Request r) throws Exception; }
