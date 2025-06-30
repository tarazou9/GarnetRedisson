package com.azure.redisson.sample;

import io.netty.resolver.AddressResolver;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapBasedAddressResolver implements AddressResolver<InetSocketAddress> {

    private final AddressResolver<InetSocketAddress> delegate;
    private final Map<String, String> aliasToIp;

    public MapBasedAddressResolver(AddressResolver<InetSocketAddress> delegate, Map<String, String> aliasToIp) {
        this.delegate = delegate;
        this.aliasToIp = new HashMap<>(aliasToIp);
    }

    @Override
    public boolean isSupported(SocketAddress address) {
        return delegate.isSupported(remap(address));
    }

    @Override
    public boolean isResolved(SocketAddress address) {
        return delegate.isResolved(remap(address));
    }

    @Override
    public Future<InetSocketAddress> resolve(SocketAddress address) {
        return delegate.resolve(remap(address));
    }

    @Override
    public Future<InetSocketAddress> resolve(SocketAddress address, Promise<InetSocketAddress> promise) {
        return delegate.resolve(remap(address), promise);
    }

    @Override
    public Future<List<InetSocketAddress>> resolveAll(SocketAddress address) {
        return delegate.resolveAll(remap(address));
    }

    @Override
    public Future<List<InetSocketAddress>> resolveAll(SocketAddress address, Promise<List<InetSocketAddress>> promise) {
        return delegate.resolveAll(remap(address), promise);
    }

    @Override
    public void close() {
        delegate.close();
    }

    private InetSocketAddress remap(SocketAddress address) {
        if (!(address instanceof InetSocketAddress)) {
            return (InetSocketAddress) address;
        }

        InetSocketAddress inetAddr = (InetSocketAddress) address;
        String hostname = inetAddr.getHostString();
        int port = inetAddr.getPort();

        String mappedIp = aliasToIp.get(hostname);
        if (mappedIp != null) {
            return new InetSocketAddress(mappedIp, port);
        }
        return inetAddr;
    }
}
