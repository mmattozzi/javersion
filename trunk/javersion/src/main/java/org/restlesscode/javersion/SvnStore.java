package org.restlesscode.javersion;

import java.util.concurrent.Semaphore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * Represents a connection to an SVN repository for storing java objects.
 */
public class SvnStore {

	private static Log LOG = LogFactory.getLog(SvnStore.class);
	
	static {
		FSRepositoryFactory.setup();
		DAVRepositoryFactory.setup();
		SVNRepositoryFactoryImpl.setup();
	}
	
	protected SVNRepository repository;
	protected SerializationTable serializationTable;
	protected Semaphore commitLock = new Semaphore(1);
	
	/**
	 * Create a new connection to a Subversion repo
	 * @param url url of the repo
	 * @throws SVNException
	 */
	public SvnStore(String url) throws SVNException {
		this(url, null, null);
	}
	
	/**
	 * Create a new connection to a subversion repo supply authentication
	 * @param url url of the repo
	 * @param username
	 * @param password
	 * @throws SVNException
	 */
	public SvnStore(String url, String username, String password) throws SVNException {
		repository = SVNRepositoryFactory.create(SVNURL.parseURIDecoded(url));
		if (username != null) {
			ISVNAuthenticationManager authManager = new BasicAuthenticationManager(username, password);
			repository.setAuthenticationManager( authManager );
		}
		serializationTable = new SerializationTable();
        initialConnectionCheck();
	}
	
	protected void initialConnectionCheck() throws SVNException {
		LOG.info("Repository root: " + repository.getRepositoryRoot(true));
		LOG.info("Repository UUID: " + repository.getRepositoryUUID(true));
	}
	
	public SerializationTable getSerializationTable() {
		return this.serializationTable;
	}
}
