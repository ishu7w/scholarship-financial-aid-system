package com.scholarai.document;

import static com.scholarai.storage.Records.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.scholarai.aid.domain.*;
import com.scholarai.storage.*;
import java.time.Instant;
import java.util.*;

public final class DocumentService {
  private final PlatformRepository repository;
  private final ObjectMapper json;
  private final VerificationService verification;

  public DocumentService(PlatformRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
    this.verification = new VerificationService(json);
  }

  public ArrayNode list(Principal user, String id) {
    own(user, id);
    ArrayNode result = json.createArrayNode();
    for (JsonNode row : repository.read().path("documents"))
      if (row.path("studentId").asText().equals(user.id())) result.insert(0, row);
    return result;
  }

  public ObjectNode save(Principal user, JsonNode input) {
    role(user, "student");
    String kind = text(input, "kind", 1, 30),
        mimeType = text(input, "mimeType", 1, 100),
        storagePath = text(input, "storagePath", 1, 400);
    if (!storagePath.startsWith(user.id() + "/"))
      throw new AidException(403, "Invalid document owner");
    String content = text(input, "text", 0, 200000);
    int size = (int) number(input, "byteSize", 1, 5242880, true);
    if (!Set.of("application/pdf", "image/png", "image/jpeg").contains(mimeType))
      throw new AidException(400, "Unsupported file type");
    return repository.update(
        state -> {
          if (input.hasNonNull("applicationId")) {
            var application =
                require(
                    state.path("applications"),
                    "id",
                    input.path("applicationId").asText(),
                    "Application not found");
            own(user, application.path("studentId").asText());
          }
          var outcome =
              verification.verify(
                  kind, mimeType, size, content, state.path("students").path(user.id()));
          ObjectNode row =
              json.createObjectNode()
                  .put("id", UUID.randomUUID().toString())
                  .put("studentId", user.id())
                  .put("kind", kind)
                  .put("storagePath", storagePath)
                  .put("verificationStatus", outcome.status())
                  .put("createdAt", Instant.now().toString());
          row.set("flags", json.valueToTree(outcome.flags()));
          row.set("extractedFields", outcome.extractedFields());
          ((ArrayNode) state.get("documents")).add(row);
          audit(state, user, "document.uploaded", "document", row.path("id").asText());
          return row;
        });
  }

  public void delete(Principal user, String id) {
    repository.update(
        state -> {
          ArrayNode documents = (ArrayNode) state.get("documents");
          var row = require(documents, "id", id, "Document not found");
          own(user, row.path("studentId").asText());
          for (int i = 0; i < documents.size(); i++)
            if (documents.get(i).path("id").asText().equals(id)) {
              documents.remove(i);
              break;
            }
          audit(state, user, "document.deleted", "document", id);
          return null;
        });
  }
}
