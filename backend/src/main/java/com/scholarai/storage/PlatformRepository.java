package com.scholarai.storage;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.function.Function;

/** Services depend on this interface, not on a particular database. */
public interface PlatformRepository {
  ObjectNode read();

  <T> T update(Function<ObjectNode, T> operation);
}
