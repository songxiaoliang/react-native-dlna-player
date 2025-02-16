/*
 * Copyright (C) 2013 4th Line GmbH, Switzerland
 *
 * The contents of this file are subject to the terms of either the GNU
 * Lesser General Public License Version 2 or later ("LGPL") or the
 * Common Development and Distribution License Version 1 or later
 * ("CDDL") (collectively, the "License"). You may not use this file
 * except in compliance with the License. See LICENSE.txt for more
 * information.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.fourthline.cling.android;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceConfiguration;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.protocol.ProtocolFactory;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.transport.Router;

/**
 * Provides a UPnP stack with Android configuration as an application service component.
 * <p>
 * Sends a search for all UPnP devices on instantiation. See the
 * {@link org.fourthline.cling.android.AndroidUpnpService} interface for a usage example.
 * </p>
 * <p/>
 * Override the {@link #createRouter(org.fourthline.cling.UpnpServiceConfiguration, org.fourthline.cling.protocol.ProtocolFactory, android.content.Context)}
 * and {@link #createConfiguration()} methods to customize the service.
 *
 * @author Christian Bauer
 */
public class AndroidUpnpServiceImpl extends Service {

    protected UpnpService upnpService;
    protected Binder binder = new Binder();

    /**
     * Starts the UPnP service.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("ObsoleteSdkInt")
    @Override
    public void onCreate() {
        super.onCreate();
        // 前台服务
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            startForeground(1, new Notification());
        } else {
            String NOTIFICATION_CHANNEL_ID = "org.fourthline.cling.android.AndroidUpnpServiceImpl";
            String channelName = "DLNA Background Service";
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(chan);

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
            Notification notification = notificationBuilder.setOngoing(true)
                    .setContentTitle("App is running in background")
                    .setPriority(NotificationManager.IMPORTANCE_MIN)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build();
            startForeground(2, notification);
        }
        upnpService = new UpnpServiceImpl(createConfiguration()) {

            @Override
            protected Router createRouter(ProtocolFactory protocolFactory, Registry registry) {
                return AndroidUpnpServiceImpl.this.createRouter(
                    getConfiguration(),
                    protocolFactory,
                    AndroidUpnpServiceImpl.this
                );
            }

            @Override
            public synchronized void shutdown() {
                // First have to remove the receiver, so Android won't complain about it leaking
                // when the main UI thread exits.
                ((AndroidRouter)getRouter()).unregisterBroadcastReceiver();

                // Now we can concurrently run the Cling shutdown code, without occupying the
                // Android main UI thread. This will complete probably after the main UI thread
                // is done.
                super.shutdown(true);
            }
        };
    }

    protected UpnpServiceConfiguration createConfiguration() {
        return new AndroidUpnpServiceConfiguration();
    }

    protected AndroidRouter createRouter(UpnpServiceConfiguration configuration,
                                                   ProtocolFactory protocolFactory,
                                                   Context context) {
        return new AndroidRouter(configuration, protocolFactory, context);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Stops the UPnP service, when the last Activity unbinds from this Service.
     */
    @Override
    public void onDestroy() {
        upnpService.shutdown();
        super.onDestroy();
    }

    protected class Binder extends android.os.Binder implements AndroidUpnpService {

        public UpnpService get() {
            return upnpService;
        }

        public UpnpServiceConfiguration getConfiguration() {
            return upnpService.getConfiguration();
        }

        public Registry getRegistry() {
            return upnpService.getRegistry();
        }

        public ControlPoint getControlPoint() {
            return upnpService.getControlPoint();
        }
    }

}