package ch.hevs.medgift.keycloak.protocol.docker.mapper;

import java.util.Set;
import java.util.stream.Collectors;

import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.docker.DockerAuthV2Protocol;
import org.keycloak.protocol.docker.mapper.DockerAuthV2AttributeMapper;
import org.keycloak.protocol.docker.mapper.DockerAuthV2ProtocolMapper;
import org.keycloak.representations.docker.DockerAccess;
import org.keycloak.representations.docker.DockerResponseToken;

/**
 * Populates token with requested scope if: 
 * -  user has admin role.
 * -  user has user role and requested resource consists of a 1-level namespace 
 *    (i.e. <namespace>/<imagename>) that matches his username.
 * If more scopes are present than what has been requested, they will be removed.
 */
public class UserNamespaceMapper extends DockerAuthV2ProtocolMapper implements DockerAuthV2AttributeMapper {

    public static final String PROVIDER_ID = "docker-v2-user-namespace-mapper";
    
    private static final String ADMIN_ROLE_NAME ="admin";
    private static final String USER_ROLE_NAME ="user";
    
    public UserNamespaceMapper() {}

    @Override
    public String getDisplayType() {
        return "User namespace mapping";
    }

    @Override
    public String getHelpText() {
		return "Allow all grants to own user namespace, returning the full set "
				+ "of requested attributes as permitted attributes. If user "
				+ "requests a resource that is not in his own namespace, deny "
				+ "all grants. A user with the role 'admin' is given all grants "
				+ "in any namespace.";
    }
    
    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean appliesTo(final DockerResponseToken responseToken) {
        return true;
    }

    @Override
    public DockerResponseToken transformDockerResponseToken(final DockerResponseToken responseToken, 
    		final ProtocolMapperModel mappingModel,
    		final KeycloakSession session, final UserSessionModel userSession, 
    		final AuthenticatedClientSessionModel clientSession) {
    	
    	//Remove all scopes
    	responseToken.getAccessItems().clear();
    	
    	final String requestedScope = clientSession.getNote(DockerAuthV2Protocol.SCOPE_PARAM);
    	
    	//If no scope is requested (e.g. login), return empty list of access items.
    	if(requestedScope == null) return responseToken;
    	
    	Set<String> userRoleNames = userSession.getUser().getRealmRoleMappings().
    			stream().map(role -> role.getName()).collect(Collectors.toSet());
    	
    	//If user does not have admin or user role, return empty list of access items
    	//(no access to requested resource).
    	if(!userRoleNames.contains(ADMIN_ROLE_NAME) && !userRoleNames.contains(USER_ROLE_NAME))
    		return responseToken;
    	
    	final DockerAccess requestedAccess = new DockerAccess(requestedScope);
    	
    	//If user has admin role, return requested Docker Access item
    	//(admin has all permissions).
    	if(userRoleNames.contains(ADMIN_ROLE_NAME)) {
    		responseToken.getAccessItems().add(requestedAccess);
    		return responseToken;
    	}
    	
    	String requestedRepo = requestedAccess.getName();    	    	
        String[] requestedRepoTokens = requestedRepo.split("/");
        
        //Repo for user must consist of a one-level namespace followed by imagename
        //e.g. <namespace>/<imagename>. If not, return empty list of access items
        //(no access to requested resource).
        if(requestedRepoTokens.length != 2)
        	return responseToken;
    	
    	String requestedRepoNamespace = requestedRepoTokens[0];
    	String username = userSession.getUser().getUsername();
    	
    	//If namepsace matches  username, return requested Docker Access item
    	//(user has all permissions on his own namespace in repo)
    	//Else, return empty list of access items (no access to requested resource).
    	if(username.equals(requestedRepoNamespace))
    		responseToken.getAccessItems().add(requestedAccess);
    	
        return responseToken;
    }
}