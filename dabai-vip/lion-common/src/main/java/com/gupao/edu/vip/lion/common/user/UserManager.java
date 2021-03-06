
package com.gupao.edu.vip.lion.common.user;

import com.gupao.edu.vip.lion.api.Constants;
import com.gupao.edu.vip.lion.api.router.ClientLocation;
import com.gupao.edu.vip.lion.api.spi.common.CacheManager;
import com.gupao.edu.vip.lion.api.spi.common.CacheManagerFactory;
import com.gupao.edu.vip.lion.api.spi.common.MQClient;
import com.gupao.edu.vip.lion.api.spi.common.MQClientFactory;
import com.gupao.edu.vip.lion.common.CacheKeys;
import com.gupao.edu.vip.lion.common.router.MQKickRemoteMsg;
import com.gupao.edu.vip.lion.common.router.RemoteRouter;
import com.gupao.edu.vip.lion.common.router.RemoteRouterManager;
import com.gupao.edu.vip.lion.tools.config.ConfigTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * 在线列表是存在redis里的，服务被kill -9的时候，无法修改redis。
 * 查询全部在线列表的时候，要通过当前ZK里可用的机器来循环查询。
 * 每台机器的在线列表是分开存的，如果都存储在一起，某台机器挂了，反而不好处理。
 */
public final class UserManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserManager.class);

    private final String onlineUserListKey = CacheKeys.getOnlineUserListKey(ConfigTools.getPublicIp());

    private final CacheManager cacheManager = CacheManagerFactory.create();

    private final MQClient mqClient = MQClientFactory.create();

    private final RemoteRouterManager remoteRouterManager;

    public UserManager(RemoteRouterManager remoteRouterManager) {
        this.remoteRouterManager = remoteRouterManager;
    }

    public void kickUser(String userId) {
        kickUser(userId, -1);
    }

    public void kickUser(String userId, int clientType) {
        Set<RemoteRouter> remoteRouters = remoteRouterManager.lookupAll(userId);
        if (remoteRouters != null) {
            for (RemoteRouter remoteRouter : remoteRouters) {
                ClientLocation location = remoteRouter.getRouteValue();
                if (clientType == -1 || location.getClientType() == clientType) {
                    MQKickRemoteMsg message = new MQKickRemoteMsg()
                            .setUserId(userId)
                            .setClientType(location.getClientType())
                            .setConnId(location.getConnId())
                            .setDeviceId(location.getDeviceId())
                            .setTargetServer(location.getHost())
                            .setTargetPort(location.getPort());
                    mqClient.publish(Constants.getKickChannel(location.getHostAndPort()), message);
                }
            }
        }
    }

    public void clearOnlineUserList() {
        cacheManager.del(onlineUserListKey);
    }

    public void addToOnlineList(String userId) {
        cacheManager.zAdd(onlineUserListKey, userId);
        LOGGER.info("user online {}", userId);
    }

    public void remFormOnlineList(String userId) {
        cacheManager.zRem(onlineUserListKey, userId);
        LOGGER.info("user offline {}", userId);
    }

    //在线用户数量
    public long getOnlineUserNum() {
        Long value = cacheManager.zCard(onlineUserListKey);
        return value == null ? 0 : value;
    }

    //在线用户数量
    public long getOnlineUserNum(String publicIP) {
        String online_key = CacheKeys.getOnlineUserListKey(publicIP);
        Long value = cacheManager.zCard(online_key);
        return value == null ? 0 : value;
    }

    //在线用户列表
    public List<String> getOnlineUserList(String publicIP, int start, int end) {
        String key = CacheKeys.getOnlineUserListKey(publicIP);
        return cacheManager.zrange(key, start, end, String.class);
    }
}
