package xyz.tcheeric.cashu.vault.db.config;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import xyz.tcheeric.cashu.vault.db.model.RevisionInfo;

/**
 * Populates {@link RevisionInfo#getPrincipalId()} from the active Spring Security
 * {@code Authentication} when Envers creates a new revision row (spec 001 / FR-013).
 * Falls back to {@code null} for system/migration/startup revisions where no
 * SecurityContext is present.
 */
public class PrincipalRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        RevisionInfo rev = (RevisionInfo) revisionEntity;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        rev.setPrincipalId(auth != null ? auth.getName() : null);
    }
}
