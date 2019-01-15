package io.hyperfoil.core.api;

import io.hyperfoil.api.session.Session;

// TODO: if this is the only class in .core.api we might move it to .api
public interface ResourceUtilizer {
   void reserve(Session session);
}
