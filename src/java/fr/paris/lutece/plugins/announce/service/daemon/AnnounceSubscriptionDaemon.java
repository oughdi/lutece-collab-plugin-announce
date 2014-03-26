/*
 * Copyright (c) 2002-2014, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.announce.service.daemon;

import fr.paris.lutece.plugins.announce.business.Announce;
import fr.paris.lutece.plugins.announce.business.AnnounceHome;
import fr.paris.lutece.plugins.announce.business.AnnounceSearchFilter;
import fr.paris.lutece.plugins.announce.business.AnnounceSearchFilterHome;
import fr.paris.lutece.plugins.announce.service.AnnounceSubscriptionProvider;
import fr.paris.lutece.plugins.announce.service.announcesearch.AnnounceSearchService;
import fr.paris.lutece.plugins.subscribe.business.Subscription;
import fr.paris.lutece.portal.service.daemon.Daemon;
import fr.paris.lutece.portal.service.datastore.DatastoreService;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 * Daemon to send notification to users when subscribed announces are created
 */
public class AnnounceSubscriptionDaemon extends Daemon
{

    private static final String DATASTORE_KEY_SUBSCRIPTION_DAEMON_LAST_RUN = "announce.announceSubscriptionDaemon.timeDaemonLastRun";

    /**
     * {@inheritDoc}
     */
    @Override
    public void run( )
    {
        String strTimeLastRun = DatastoreService.getDataValue( DATASTORE_KEY_SUBSCRIPTION_DAEMON_LAST_RUN, "0" );

        long lTimeLastRun;
        try
        {
            lTimeLastRun = Long.parseLong( strTimeLastRun );
        }
        catch ( NumberFormatException nfe )
        {
            lTimeLastRun = 0l;
            DatastoreService.removeData( DATASTORE_KEY_SUBSCRIPTION_DAEMON_LAST_RUN );
        }

        List<Integer> listIdAnnounces = AnnounceHome.findIdAnnouncesByDatePublication( lTimeLastRun );
        // We save the current time
        DatastoreService.setDataValue( DATASTORE_KEY_SUBSCRIPTION_DAEMON_LAST_RUN,
                Long.toString( System.currentTimeMillis( ) ) );

        if ( listIdAnnounces != null && listIdAnnounces.size( ) > 0 )
        {
            // We get the list of announces
            List<Announce> listAnnounce = AnnounceHome.findByListId( listIdAnnounces );

            // We associated announces to the id of the associated category
            Map<Integer, List<Announce>> mapAnnouncesByCategories = new HashMap<Integer, List<Announce>>(
                    listAnnounce.size( ) );
            Map<Integer, Announce> mapAnnouncesById = new HashMap<Integer, Announce>( listAnnounce.size( ) );
            Map<String, List<Announce>> mapAnnouncesByUsers = new HashMap<String, List<Announce>>( listAnnounce.size( ) );
            for ( Announce announce : listAnnounce )
            {
                List<Announce> listMapAnnounce = mapAnnouncesByCategories.get( announce.getCategory( ).getId( ) );
                if ( listMapAnnounce == null )
                {
                    listMapAnnounce = new ArrayList<Announce>( );
                    mapAnnouncesByCategories.put( announce.getCategory( ).getId( ), listMapAnnounce );
                }
                listMapAnnounce.add( announce );

                listMapAnnounce = mapAnnouncesByUsers.get( announce.getUserName( ) );
                if ( listMapAnnounce == null )
                {
                    listMapAnnounce = new ArrayList<Announce>( );
                    mapAnnouncesByUsers.put( announce.getUserName( ), listMapAnnounce );
                }
                listMapAnnounce.add( announce );

                mapAnnouncesById.put( announce.getId( ), announce );
            }

            // We create a map that will user names with recently published announces they subscribed to  
            Map<String, List<Announce>> mapUserAnnounces = new HashMap<String, List<Announce>>( );

            List<Subscription> listSubscriptions = AnnounceSubscriptionProvider.getService( ).getSubscriptionsToUsers( );
            for ( Subscription subscription : listSubscriptions )
            {
                List<Announce> listAnnouncesToAdd = mapAnnouncesByUsers.get( subscription.getIdSubscribedResource( ) );
                if ( listAnnouncesToAdd != null && listAnnouncesToAdd.size( ) > 0 )
                {
                    addAnnounceToMap( listAnnouncesToAdd, subscription.getUserId( ), mapUserAnnounces );
                }
            }

            listSubscriptions = AnnounceSubscriptionProvider.getService( ).getsubscriptionsToCategories( );
            for ( Subscription subscription : listSubscriptions )
            {
                List<Announce> listAnnouncesToAdd = mapAnnouncesByCategories.get( Integer.parseInt( subscription
                        .getIdSubscribedResource( ) ) );
                if ( listAnnouncesToAdd != null && listAnnouncesToAdd.size( ) > 0 )
                {
                    addAnnounceToMap( listAnnouncesToAdd, subscription.getUserId( ), mapUserAnnounces );
                }
            }

            listSubscriptions = AnnounceSubscriptionProvider.getService( ).getSubscriptionsToFilters( );

            for ( Subscription subscription : listSubscriptions )
            {
                AnnounceSearchFilter filter = AnnounceSearchFilterHome.findByPrimaryKey( Integer.parseInt( subscription
                        .getIdSubscribedResource( ) ) );
                if ( filter.getDateMin( ) == null || filter.getDateMin( ).getTime( ) < lTimeLastRun )
                {
                    filter.setDateMin( new Date( lTimeLastRun ) );
                }
                List<Integer> listIdFilteredAnnounces = new ArrayList<Integer>( );
                AnnounceSearchService.getInstance( ).getSearchResults( filter, 0, 0, listIdFilteredAnnounces );
                addAnnounceToMap( getListAnnouncesFromId( listIdFilteredAnnounces, mapAnnouncesById ),
                        subscription.getUserId( ), mapUserAnnounces );
            }

            for ( Entry<String, List<Announce>> entry : mapUserAnnounces.entrySet( ) )
            {
                notifyUser( entry.getKey( ), entry.getValue( ) );
            }
        }
    }

    /**
     * Get the list of announces from a list of id
     * @param listIdFilteredAnnounces The list of ids to get
     * @param mapAnnouncesById The map of announces. If an announce which id is
     *            in the id list is not in the map, then it is loaded from the
     *            database
     * @return the list of announces
     */
    private List<Announce> getListAnnouncesFromId( List<Integer> listIdFilteredAnnounces,
            Map<Integer, Announce> mapAnnouncesById )
    {
        if ( listIdFilteredAnnounces == null || listIdFilteredAnnounces.size( ) == 0 )
        {
            return new ArrayList<Announce>( );
        }
        List<Announce> listAnnounces = new ArrayList<Announce>( listIdFilteredAnnounces.size( ) );
        for ( Integer nIdAnnounce : listIdFilteredAnnounces )
        {
            Announce announce = mapAnnouncesById.get( nIdAnnounce );
            if ( announce == null )
            {
                announce = AnnounceHome.findByPrimaryKey( nIdAnnounce );
                mapAnnouncesById.put( nIdAnnounce, announce );
            }
            listAnnounces.add( announce );
        }
        return listAnnounces;
    }

    /**
     * Associates an announce with a user in a map
     * @param listAnnounce The announce
     * @param strUserName The name of the user
     * @param map The map
     */
    private void addAnnounceToMap( List<Announce> listAnnounce, String strUserName, Map<String, List<Announce>> map )
    {
        List<Announce> listMapAnnounces = map.get( strUserName );
        if ( listMapAnnounces == null )
        {
            listMapAnnounces = new ArrayList<Announce>( );
            map.put( strUserName, listMapAnnounces );
        }
        listMapAnnounces.addAll( listAnnounce );
    }

    /**
     * Notify a user that announces ha has subscribed to have been published
     * @param strUserName The name of the user to notify
     * @param listAnnouncesToNotify The list of published announces
     */
    private void notifyUser( String strUserName, List<Announce> listAnnouncesToNotify )
    {
        // TODO Auto-generated method stub

    }
}
