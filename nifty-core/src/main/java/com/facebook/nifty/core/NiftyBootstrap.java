/*
 * Copyright (C) 2012-2013 Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.nifty.core;

import com.google.inject.Inject;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A lifecycle object that manages starting up and shutting down multiple core channels.
 */
public class NiftyBootstrap
{
    private final ChannelGroup allChannels;
    private ArrayList<NettyServerTransport> transports;
    private ExecutorService bossExecutor;
    private ExecutorService workerExecutor;
    private final Timer timer;
    private NioServerSocketChannelFactory serverChannelFactory;

    /**
     * This takes a Set of ThriftServerDef. Use Guice Multibinder to inject.
     */
    @Inject
    public NiftyBootstrap(
            Set<ThriftServerDef> thriftServerDefs,
            NettyConfigBuilder configBuilder,
            ChannelGroup allChannels)
    {
        this.allChannels = allChannels;
        this.transports = new ArrayList<>();
        this.timer = new HashedWheelTimer();
        for (ThriftServerDef thriftServerDef : thriftServerDefs) {
            transports.add(new NettyServerTransport(thriftServerDef,
                                                    configBuilder,
                                                    allChannels,
                                                    timer));
        }

    }

    @PostConstruct
    public void start()
    {
        bossExecutor = Executors.newCachedThreadPool();
        workerExecutor = Executors.newCachedThreadPool();
        serverChannelFactory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor);
        for (NettyServerTransport transport : transports) {
            transport.start(serverChannelFactory);
        }
    }

    @PreDestroy
    public void stop()
    {
        for (NettyServerTransport transport : transports) {
            try {
                transport.stop();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        ShutdownUtil.shutdownChannelFactory(serverChannelFactory, bossExecutor,
                                            workerExecutor, allChannels);
    }
}
