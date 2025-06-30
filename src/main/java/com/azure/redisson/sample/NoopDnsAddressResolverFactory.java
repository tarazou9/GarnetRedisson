package com.azure.redisson.sample;

import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.dns.DnsServerAddressStreamProvider;
import io.netty.util.concurrent.EventExecutor;

import java.net.InetSocketAddress;
import java.util.Map;

import org.redisson.connection.DnsAddressResolverGroupFactory;

import io.netty.resolver.dns.DnsAddressResolverGroup;

public class NoopDnsAddressResolverFactory extends DnsAddressResolverGroupFactory {

    @Override
    public DnsAddressResolverGroup create(
        Class<? extends DatagramChannel> channelType,
        Class<? extends SocketChannel> socketChannelType,
        DnsServerAddressStreamProvider nameServerProvider
    ) {
        Map<String, String> aliasMap = NodeAliasMapper.HOST_ALIAS_MAP;

        // Returning a no-op resolver group to completely bypass DNS resolution
        return new DnsAddressResolverGroup(channelType, nameServerProvider) {
            @Override
            public AddressResolver<InetSocketAddress> getResolver(EventExecutor executor) {
                AddressResolver<InetSocketAddress> delegate = super.getResolver(executor);
                return new MapBasedAddressResolver(delegate, aliasMap);
            }
        };
    }
}
