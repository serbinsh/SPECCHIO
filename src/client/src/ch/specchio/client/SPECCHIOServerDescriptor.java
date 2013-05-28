package ch.specchio.client;

import java.io.IOException;
import java.io.Writer;

import ch.specchio.types.User;


/**
 * This interface must be implemented by all server descriptors.
 */
public interface SPECCHIOServerDescriptor {
	
	/**
	 * Create a client object suitable for connecting to the server
	 * described by this descriptor.
	 * 
	 * @return a new SPECCHIOClient object
	 */
	public SPECCHIOClient createClient();
	
	
	/**
	 * Get the display name of this server.
	 * 
	 * @return a string describing the server, suitable for display to the user
	 */
	public String getDisplayName();
	
	
	/**
	 * Get a description of the user account under which we are to log in.
	 * 
	 * @return a string describing the user account under which this client is to log in
	 */
	public String getDisplayUser();
	
	
	/**
	 * Set the user information associated with this account.
	 * 
	 * @param user	the user information
	 */
	public void setUser(User user);
	
	
	/**
	 * Write the configuration of a user account.
	 * 
	 * @param w		the writer with which to write
	 * 
	 * @throws IOException	I/O error
	 */
	public void writeAccountConfiguration(Writer w) throws IOException;

}