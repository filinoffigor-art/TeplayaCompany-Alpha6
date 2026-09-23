package ru.teplayakompaniya.tk4;

import org.json.JSONObject;
import org.json.JSONException;
import java.util.UUID;

/** Opt-in backend v1 commands. Never send them to an endpoint without matching capabilities. */
public final class Stage1Contracts {
    private Stage1Contracts() {}
    public static JSONObject command(String action, String reason) throws JSONException {
        JSONObject command = new JSONObject();
        command.put("action", action);
        command.put("schemaVersion", 1);
        command.put("idempotencyKey", UUID.randomUUID().toString());
        command.put("reason", reason == null ? "" : reason.trim());
        return command;
    }
    public static final class CrmIds {
        public final String clientId, leadId, dealId, surveyId, objectId;
        public CrmIds(String clientId, String leadId, String dealId, String surveyId, String objectId) {
            this.clientId=clientId;this.leadId=leadId;this.dealId=dealId;this.surveyId=surveyId;this.objectId=objectId;
        }
        public JSONObject json() throws JSONException {
            JSONObject json=new JSONObject();json.put("Client_ID",clientId);json.put("Lead_ID",leadId);json.put("Deal_ID",dealId);json.put("Survey_ID",surveyId);json.put("Object_ID",objectId);return json;
        }
    }
}
