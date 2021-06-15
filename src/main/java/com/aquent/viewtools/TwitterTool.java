package com.aquent.viewtools;

import com.dotcms.security.apps.AppSecrets;
import com.dotcms.security.apps.Secret;
import io.vavr.control.Try;
import org.apache.velocity.tools.view.tools.ViewTool;

import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;

import twitter4j.PagableResponseList;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.conf.ConfigurationBuilder;

import java.util.Map;
import java.util.Optional;

/**
 * Exposes Twitter4J to dotCMS in a viewtool
 * Mapped to $twitter
 * 
 * @author cfalzone
 *
 */
public class TwitterTool implements ViewTool {

	private Twitter twitter;
    private boolean inited = false;
    
    public void init(Object initData) { 
    	Logger.debug(this, "Twitter Tool Starting Up");
    	
    	// Get the default host
    	Host defaultHost;
		try {
			defaultHost = APILocator.getHostAPI().findDefaultHost(APILocator.getUserAPI().getSystemUser(), false);
		} catch (Exception e) {
			Logger.error(this, "Unable to get the default host", e);
			return;
		}

		Logger.debug(this, "Default Host = "+defaultHost.getHostname());

		final Optional<AppSecrets> appSecrets = Try.of(
				() -> APILocator.getAppsAPI().getSecrets(AppKeys.APP_KEY, true, defaultHost, APILocator.systemUser()))
				.getOrElse(Optional.empty());

		if (!appSecrets.isPresent()) {

			Logger.error(this, "Unable to get config for the default host");
			return ;
		}

		final boolean debug         = Try.of(()->appSecrets.get().getSecrets().get(AppKeys.DEBUG.key).getBoolean()).getOrElse(Boolean.FALSE);
		final String consumerKey    = Try.of(()->appSecrets.get().getSecrets().get(AppKeys.CONSUMER_KEY.key).getString()).getOrNull();
		final String consumerSecret = Try.of(()->appSecrets.get().getSecrets().get(AppKeys.CONSUMER_SECRET.key).getString()).getOrNull();
		final String accessToken    = Try.of(()->appSecrets.get().getSecrets().get(AppKeys.ACCESS_TOKEN.key).getString()).getOrNull();
		final String tokenSecret    = Try.of(()->appSecrets.get().getSecrets().get(AppKeys.TOKEN_SECRET.key).getString()).getOrNull();
		
		Logger.debug(this, "Twitter Auth - CK="+consumerKey+", CKS="+consumerSecret+", AT="+accessToken+", ATS="+tokenSecret);
		
		final ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(debug)
		  .setOAuthConsumerKey(consumerKey)
		  .setOAuthConsumerSecret(consumerSecret)
		  .setOAuthAccessToken(accessToken)
		  .setOAuthAccessTokenSecret(tokenSecret);
    	
		Logger.debug(this, "Twitter Configuration: " + cb);
		
        try {
            twitter = new TwitterFactory(cb.build()).getInstance(); 
        } catch (Exception e) {
            Logger.error(this, "Error getting twitter instance", e);
            return;
        } finally {
        	appSecrets.get().destroy();
		}
        
        inited = true;
        Logger.info(this, "Twitter Tool Started Up");
    }
    
    /**
     * Returns the twitter object - use with care
     * 
     * @return	The twitter object
     */
    public Twitter getTwitter() {
    	if(inited) {
    		return twitter;
    	} else {
    		Logger.warn(this, "ViewTool not inited");
    		return null;
    	}
    }
 
    /**
     * Fetched the last count tweets for the Screen Name
     * 
     * See {@link twitter4j.Twitter.getUserTimeline}
     * 
     * @param screenName    The screen name to fetch the tweets for
     * @param pageParam		The page of results to pull, if empty 1
     * @param countParam    The number of results to pull per page, if empty 20
     * @return              A list of the last count tweets for the the screen name, or null if something went wrong
     */
    public ResponseList<Status> getUserTimeline(final String screenName,
												final int pageParam,
												final int countParam) {
        if(inited) {

        	final int page  = !UtilMethods.isSet(pageParam)?pageParam:1;
        	final int count = !UtilMethods.isSet(countParam)?countParam:20;

        	// See if this username is a miss
        	final boolean isMiss = TwitterToolMissCacheGroupHandler.INSTANCE.get(screenName);
        	if(isMiss) {

        		Logger.debug(this, "Miss Cached with handle: "+screenName);
        		return null;
        	}
        	
            try {

                return twitter.getUserTimeline(screenName, new Paging(page, count));
            } catch (TwitterException e) {

                Logger.error(this, "Error Fetching timeline for handle: "+screenName+" errorCode: "+e.getErrorCode(), e);
                if(e.getErrorCode() == 34) {
                	Logger.debug(this, "Adding "+screenName+" to the miss cache");
                	TwitterToolMissCacheGroupHandler.INSTANCE.put(screenName, true);
                }
                return null;
            }
        } else {
            Logger.warn(this, "ViewTool not inited");
            return null;
        }
    }
    
    /**
     * Fetches the last count tweets for the user ID
     * 
     * See {@link twitter4j.Twitter.getUserTimeline}
     * 
     * @param userId     The User ID to fetch the tweets for
     * @param pageParam	 The page of results to pull, if empty 1
     * @param countParam The number of results to pull per page, if empty 20
     * @return          A list of the last count tweets for the the user id, or null if something went wrong
     */
    public ResponseList<Status> getUserTimeline(final long userId,
												final int pageParam,
												final int countParam) {
        if(inited) {

			final int page  = !UtilMethods.isSet(pageParam)?pageParam:1;
			final int count = !UtilMethods.isSet(countParam)?countParam:20;
        	
        	// See if this username is a miss
        	final boolean isMiss = TwitterToolMissCacheGroupHandler.INSTANCE.get(String.valueOf(userId));
        	if(isMiss) {
        		Logger.debug(this, "Miss Cached with userId: "+userId);
        		return null;
        	}
        	
            try {
                return twitter.getUserTimeline(userId, new Paging(page, count));
            } catch (TwitterException e) {
                Logger.error(this, "Error Fetching timeline for userId: "+userId+" errorCode: "+e.getErrorCode(), e);
                if(e.getErrorCode() == 34) {
                	Logger.debug(this, "Adding "+userId+" to the miss cache");
                	TwitterToolMissCacheGroupHandler.INSTANCE.put(String.valueOf(userId), true);
                }
                return null;
            }
        } else {
            Logger.warn(this, "ViewTool not inited");
            return null;
        }
    }
    
    /**
     * Returns a Twitter4J User object for the screen name
     * 
     * See {@link twitter4j.Twitter.showUser}
     * 
     * @param screenName    The screen name to look for
     * @return              A Twitter4J User object for the screen name or null if something went wrong
     */
    public User showUser(final String screenName) {
    	if(inited) {
    		// See if this username is a miss
        	final boolean isMiss = TwitterToolMissCacheGroupHandler.INSTANCE.get(screenName);
        	if(isMiss) {
        		Logger.debug(this, "Miss Cached with handle: "+screenName);
        		return null;
        	}
        	
    		try {
				return twitter.showUser(screenName);
    		} catch (TwitterException e) {
                Logger.error(this, "Error Fetching user for handle: "+screenName+" errorCode: "+e.getErrorCode(), e);
                if(e.getErrorCode() == 34) {
                	Logger.debug(this, "Adding "+screenName+" to the miss cache");
                	TwitterToolMissCacheGroupHandler.INSTANCE.put(screenName, true);
                }
                return null;
            }
    	} else {
    		Logger.warn(this, "ViewTool not inited");
    		return null;
    	}
    }
    
    /**
     * Returns a Twitter4J User object for the userId
     * 
     * See {@link twitter4j.Twitter.showUser}
     * 
     * @param screenName    The screen name to look for
     * @return              A Twitter4J User object for the screen name or null if something went wrong
     */
    public User showUser(final long userId) {
    	if(inited) {
    		// See if this username is a miss
        	final boolean isMiss = TwitterToolMissCacheGroupHandler.INSTANCE.get(String.valueOf(userId));
        	if(isMiss) {
        		Logger.debug(this, "Miss Cached with userId: "+userId);
        		return null;
        	}
        	
    		try {
				return twitter.showUser(userId);
    		} catch (TwitterException e) {
                Logger.error(this, "Error Fetching user for userId: "+userId+" errorCode: "+e.getErrorCode(), e);
                if(e.getErrorCode() == 34) {
                	Logger.debug(this, "Adding "+userId+" to the miss cache");
                	TwitterToolMissCacheGroupHandler.INSTANCE.put(String.valueOf(userId), true);
                }
                return null;
            }
    	} else {
    		Logger.warn(this, "ViewTool not inited");
    		return null;
    	}
    }
    
    /**
     * Gets a list of up to 20 followers for the user
     * 
     * See {@link twitter4j.Twitter.getFollowersList}
     * 
     * @param screenName   The screen name to get a list of follower ids for
     * @return             A list of up to 20 of the user's followers
     */
    public PagableResponseList<User> getFollowersList(final String screenName) {
    	if(inited) {
    		// See if this username is a miss
        	final boolean isMiss = TwitterToolMissCacheGroupHandler.INSTANCE.get(screenName);
        	if(isMiss) {
        		Logger.debug(this, "Miss Cached with handle: "+screenName);
        		return null;
        	}
        	
    		try {
				return twitter.getFollowersList(screenName, -1);
    		} catch (TwitterException e) {
                Logger.error(this, "Error Fetching followers for handle: "+screenName+" errorCode: "+e.getErrorCode(), e);
                if(e.getErrorCode() == 34) {
                	Logger.debug(this, "Adding "+screenName+" to the miss cache");
                	TwitterToolMissCacheGroupHandler.INSTANCE.put(screenName, true);
                }
                return null;
            }
    	} else {
    		Logger.warn(this, "ViewTool not inited");
    		return null;
    	}
    }
    
    /**
     * Gets a list of up to 20 followers for the user
     * 
     * See {@link twitter4j.Twitter.getFollowersList}
     * 
     * @param userId    The userid to get a list of follower ids for
     * @return          A List of up to 20 of the user followers
     */
    public PagableResponseList<User> getFollowersList(final long userId) {
    	if(inited) {
    		// See if this username is a miss
        	final boolean isMiss = TwitterToolMissCacheGroupHandler.INSTANCE.get(String.valueOf(userId));
        	if(isMiss) {
        		Logger.debug(this, "Miss Cached with userId: "+userId);
        		return null;
        	}
        	
    		try {
				return twitter.getFollowersList(userId, -1);
    		} catch (TwitterException e) {
                Logger.error(this, "Error Fetching followers for userId: "+userId+" errorCode: "+e.getErrorCode(), e);
                if(e.getErrorCode() == 34) {
                	Logger.debug(this, "Adding "+userId+" to the miss cache");
                	TwitterToolMissCacheGroupHandler.INSTANCE.put(String.valueOf(userId), true);
                }
                return null;
            }
    	} else {
    		Logger.warn(this, "ViewTool not inited");
    		return null;
    	}
    }
    
    /**
     * Gets a list of up to 20 members for the user's list
     * 
     * See {@link twitter4j.Twitter.getUserListMembers}
     * 
     * @param ownerScreenName    The list owner's screen name
     * @param slug				 The list's slug
     * @return					 A list of up to 20 of the list's members
     */
    public PagableResponseList<User> getUserListMembers(final String ownerScreenName, final String slug) {
    	if(inited) {
    		// See if this username is a miss
        	final boolean isMiss = TwitterToolMissCacheGroupHandler.INSTANCE.get(ownerScreenName);
        	if(isMiss) {
        		Logger.debug(this, "Miss Cached with handle: "+ownerScreenName);
        		return null;
        	}
    		
    		try {
				return twitter.getUserListMembers(ownerScreenName, slug, -1);
    		} catch (TwitterException e) {
                Logger.error(this, "Error Fetching userlist members for handle: "+ownerScreenName+" errorCode: "+e.getErrorCode(), e);
                if(e.getErrorCode() == 34) {
                	Logger.debug(this, "Adding "+ownerScreenName+" to the miss cache");
                	TwitterToolMissCacheGroupHandler.INSTANCE.put(ownerScreenName, true);
                }
                return null;
            }
    	} else {
    		Logger.warn(this, "ViewTool not inited");
    		return null;
    	}
    }
    
    /**
     * Gets a list of up to 20 members for the user's list
     * 
     * See {@link twitter4j.Twitter.getUserListMembers}
     * 
     * @param ownerId   The list owner's id
     * @param slug		The list's slug
     * @return			A list of up to 20 of the list's members
     */
    public PagableResponseList<User> getUserListMembers(final long ownerId, final String slug) {
    	if(inited) {
    		// See if this username is a miss
        	final boolean isMiss = TwitterToolMissCacheGroupHandler.INSTANCE.get(String.valueOf(ownerId));
        	if(isMiss) {
        		Logger.debug(this, "Miss Cached with userId: "+ownerId);
        		return null;
        	}
        	
    		try {
				return twitter.getUserListMembers(ownerId, slug, -1);
    		} catch (TwitterException e) {
                Logger.error(this, "Error Fetching userlist members for userId: "+ownerId+" errorCode: "+e.getErrorCode(), e);
                if(e.getErrorCode() == 34) {
                	Logger.debug(this, "Adding "+ownerId+" to the miss cache");
                	TwitterToolMissCacheGroupHandler.INSTANCE.put(String.valueOf(ownerId), true);
                }
                return null;
            }
    	} else {
    		Logger.warn(this, "ViewTool not inited");
    		return null;
    	}
    }
    
    /**
     * Gets a list of tweets from a user's list 
     * 
     * See {@link twitter4j.Twitter.getUserListStatuses}
     * 
     * @param ownerScreenName    The list owner's screen name
     * @param slug               The list's slug
     * @param pageParam          The page to pull
     * @param countParam         The number of items to pull per page
     * @return                   A list of the last count statuses for the user's list
     */
    public ResponseList<Status> getUserListStatuses(final String ownerScreenName,
													final String slug,
													final int pageParam,
													final int countParam) {
    	if(inited) {

			final int page  = !UtilMethods.isSet(pageParam)?pageParam:1;
			final int count = !UtilMethods.isSet(countParam)?countParam:20;
        	
        	// See if this username is a miss
        	final boolean isMiss = TwitterToolMissCacheGroupHandler.INSTANCE.get(ownerScreenName);
        	if(isMiss) {
        		Logger.debug(this, "Miss Cached with handle: "+ownerScreenName);
        		return null;
        	}
        	
            try {
                return twitter.getUserListStatuses(ownerScreenName, slug, new Paging(page, count));
            } catch (TwitterException e) {
                Logger.error(this, "Error Fetching tweets for handle: "+ownerScreenName+" errorCode: "+e.getErrorCode(), e);
                if(e.getErrorCode() == 34) {
                	Logger.debug(this, "Adding "+ownerScreenName+" to the miss cache");
                	TwitterToolMissCacheGroupHandler.INSTANCE.put(ownerScreenName, true);
                }
                return null;
            }
        } else {
            Logger.warn(this, "ViewTool not inited");
            return null;
        }
    }
    
    /**
     * Gets a list of tweets from a user's list 
     * 
     * See {@link twitter4j.Twitter.getUserListStatuses}
     * 
     * @param ownerId    The list owner's id
     * @param slug       The list's slug
     * @param pageParam  The page to pull
     * @param countParam The number of items to pull per page
     * @return           A list of the last count statuses for the user's list
     */
    public ResponseList<Status> getUserListStatuses(long ownerId, String slug, int pageParam, int countParam) {
    	if(inited) {

			final int page  = !UtilMethods.isSet(pageParam)?pageParam:1;
			final int count = !UtilMethods.isSet(countParam)?countParam:20;
        	
        	// See if this username is a miss
        	final boolean isMiss = TwitterToolMissCacheGroupHandler.INSTANCE.get(String.valueOf(ownerId));
        	if(isMiss) {
        		Logger.debug(this, "Miss Cached with userId: "+ownerId);
        		return null;
        	}
        	
            try {
                return twitter.getUserListStatuses(ownerId, slug, new Paging(page, count));
            } catch (TwitterException e) {
                Logger.error(this, "Error Fetching tweets for userId: "+ownerId+" errorCode: "+e.getErrorCode(), e);
                if(e.getErrorCode() == 34) {
                	Logger.debug(this, "Adding "+ownerId+" to the miss cache");
                	TwitterToolMissCacheGroupHandler.INSTANCE.put(String.valueOf(ownerId), true);
                }
                return null;
            }
        } else {
            Logger.warn(this, "ViewTool not inited");
            return null;
        }
    }

}
