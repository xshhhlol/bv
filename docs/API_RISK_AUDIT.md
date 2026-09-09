# B 站接口风控检查记录

检查日期：2026-09-08 至 2026-09-09。覆盖 `bili-api` 五个 HTTP 客户端，以及仓库层分页、首页加载、直播 WebSocket、gRPC 元数据和播放器下载调用。
参考：[`fantasytyx/bv`](https://github.com/fantasytyx/bv/tree/73329dd9a99306acf9b88f99eaed9d7a6e9ef97b)。

## 验证边界

真实请求使用本机网络、未登录状态，串行且最终复测请求之间至少间隔 1.25 秒（最初基线为 0.75 秒）。未发送点赞、投币、收藏修改、关注修改、历史上报、短信或登录提交；这些路径检查请求构造和通用重试规则。没有测试账号或自定义代理配置，因此不能据此证明登录态、代理和 gRPC 业务全链路可用。接口成功只代表本次观测，不能保证服务端不再要求验证。

## 修复

- 直播弹幕 token 接口缺少 WBI 签名和 `type=0`，已按参考分支补齐；错误不再伪装成协程取消。
- UP 资料迁移到 WBI 接口，兼容不再返回私有亲密度字段的粉丝勋章摘要。UP 投稿首页遇到风控时可尝试已有 App 投稿接口；后续分页固定使用相同 API 和游标，分页中途不混用协议。
- 所有 HTTP JSON 解码前统一识别 `-352`、`-412`、`-509`、`v_voucher`、`is_risk`、`gaia_res_type`；HTTP 412/429 单独识别。压缩响应解压后只读取一次，不干扰二进制下载。
- 搜索携带已登录账号的 SESSDATA，直连和显式代理路径都保留会话；未登录不拼接空凭据。
- 无公开收藏夹的 `code=0,data=null` 按空收藏夹列表处理；风控占位数据仍由统一校验拦截。
- App GET 参数只编码一次，GET/POST 重新签名时先移除旧签名；非表单 POST 不再强制转换。UA 按实际 Web/App 请求选择。
- WBI 密钥成对原子更新；失败刷新共享一分钟冷却，只有密钥确实改变且没有验证挑战才重试一次。没有变化的 Cookie 不反复落盘，新 buvid3 同步给仓库层。
- 通用自动重试限于 GET 的 I/O 异常，写请求、业务风控和取消不重试。首页推荐失败后立即停止补页；关注列表改为逐页加载，避免全页并发。
- 分区广告位 `null` 作为无广告处理，避免解析失败影响页面加载。设备 Cookie 只补给官方 API 主机；视频详情保留参考分支的指纹 Cookie 排除逻辑。

## 最终实测结果

共记录 **58 组**只读验证（部分组含多次请求）；静态清单为 **88 个 HTTP 调用点**，二者不等同。最终应用 `compileDefaultReleaseKotlin` 通过；17 项离线回归测试通过（签名 4、响应处理 9、UA 1、投稿分页 3）。

- 视频详情、普通播放地址、播放信息、弹幕 XML、推荐、分区、六类影视索引、番剧播放地址 v1/v2、直播三个接口均有成功结果。
- UP 资料从旧接口 `-799` 修正到新接口 `code=0`；补齐字段兼容后解析成功。
- UP Web 投稿一轮返回 30 条，另一轮触发限制后仓库层切换 App，分页为 20 条和 19 条，重叠 aid 数为 0。
- 搜索热词、联想、综合搜索、用户搜索、影视搜索均有成功结果；视频、番剧和特殊字符搜索在部分轮次仍收到服务端验证挑战。**没有把它们标记为彻底修复或保证可用。** 当前不自动解答验证码，也不重复发送相同请求；登录态会话已修正但本机无账号，尚未验证登录后的表现。
- 历史、稍后再看、动态、关注关系等访客请求返回 `-101`，属于缺少登录条件。追番列表返回 `53013`，未按成功计入；没有擅自修改访问权限。
- 样本 UP 无公开收藏夹，空列表已正常返回，收藏夹信息/资源/ID 明细因此未发送，仍需可访问的样本验证。

以下为最终观测；对定向复测项使用其最新结果覆盖前一轮结果。

| 验证组 | 最终观测 |
| --- | --- |
| `video/info` | code=0 data=true |
| `video/detail` | code=0 data=true |
| `video/playurl` | code=0 data=true |
| `video/player-info` | code=0 data=true |
| `video/related` | OK  |
| `video/tags` | code=0 data=true |
| `video/shot-web` | code=0 data=true |
| `video/shot-app` | code=0 data=true |
| `video/danmaku-xml` | OK  |
| `home/popular` | code=0 data=true |
| `home/recommend-web` | code=0 data=true |
| `home/recommend-app` | code=0 data=true |
| `user/info` | code=0 data=true |
| `user/card` | code=0 data=true |
| `user/space-web` | OK count=30 |
| `user/space-app` | code=0 data=true |
| `user/relation-stat` | code=0 data=true |
| `user/followings` | code=-101 data=false |
| `search/hot-web` | code=0 data=true |
| `search/hot-app` | code=0 data=true |
| `search/trend-app` | code=0 data=true |
| `search/suggest` | OK |
| `search/all` | code=0 data=true |
| `search/video` | ERROR RiskControlException: 请求需要风控验证，请稍后重试 |
| `search/bili_user` | OK count=20 |
| `search/media_bangumi` | ERROR RiskControlException: 请求需要风控验证，请稍后重试 |
| `search/media_ft` | OK count=12 |
| `search/special-characters` | ERROR RiskControlException: 请求需要风控验证，请稍后重试 |
| `region/recommend` | code=0 data=true |
| `region/locations` | OK  |
| `region/app` | code=0 data=true |
| `pgc/season` | code=0 data=true |
| `pgc/timeline-web` | code=0 data=true |
| `pgc/timeline-app` | code=0 data=true |
| `pgc/feed-v3` | code=0 data=true |
| `pgc/feed` | code=0 data=true |
| `pgc/index-anime` | code=0 data=true |
| `pgc/index-movie` | code=0 data=true |
| `live/room-info` | code=0 data=true |
| `live/danmaku-info` | code=0 data=true |
| `live/danmaku-history` | code=0 data=true |
| `extra/user-space-pagination` | OK api=App first=20 next=19 duplicates=0 |
| `extra/pgc-playurls` | OK v1=0 v2=0 |
| `extra/season-app` | code=0 data=true |
| `extra/index-guochuang` | code=0 data=true |
| `extra/index-tv` | code=0 data=true |
| `extra/index-variety` | code=0 data=true |
| `extra/index-documentary` | code=0 data=true |
| `extra/tag-detail` | code=0 data=true |
| `extra/tag-top` | OK  |
| `extra/region-app-page` | code=0 data=true |
| `extra/public-favorites` | OK no public folders |
| `extra/self-info-guest` | code=-101 data=false |
| `extra/history-guest` | code=-101 data=false |
| `extra/watch-later-guest` | code=-101 data=false |
| `extra/dynamic-guest` | code=-101 data=false |
| `extra/relations-guest` | ERROR AuthFailureException: 账号未登录 |
| `extra/following-seasons` | code=53013 data=false |

## 可重复验证

离线回归（不调用 B 站）：

```sh
./gradlew :bili-api:test --tests '*ApiSignTest' --tests '*ApiResponseValidationTest' --tests '*ApiUserAgentTest' --tests '*SpaceVideoLoaderTest'
```

显式开启公开接口实测（会请求 B 站，默认测试不会运行）：

```sh
BILI_API_LIVE_AUDIT=true ./gradlew :bili-api:test --tests '*LiveApiAuditTest' --rerun-tasks
```

可用 `BILI_API_AUDIT_FILTER=live/` 等字符串限制验证项，也支持逗号分隔多个过滤词。输出位于 `bili-api/build/test-results/test/TEST-dev.aaa1115910.biliapi.http.LiveApiAuditTest.xml`，以 `AUDIT` 行记录各项结果。该测试用于采集结果，Gradle 成功不等同于每个在线接口成功。

## HTTP 调用清单

下表由当前代码调用点提取；包含重载及动态路径，不把清单数量当作已实测数量。

| 客户端 | 方法 | 请求 | 路径 |
| --- | --- | --- | --- |
| BiliHttpApi | `ensureWebCookies` | GET | `/x/frontend/finger/spi` |
| BiliHttpApi | `ensureWebCookies` | POST | `/bapis/bilibili.api.ticket.v1.Ticket/GenWebTicket` |
| BiliHttpApi | `getPopularVideoData` | GET | `/x/web-interface/popular` |
| BiliHttpApi | `getVideoInfo` | GET | `/x/web-interface/view` |
| BiliHttpApi | `getVideoDetail` | GET | `/x/web-interface/wbi/view/detail` |
| BiliHttpApi | `getVideoPlayUrl` | GET | `/x/player/playurl` |
| BiliHttpApi | `getPgcVideoPlayUrl` | GET | `/pgc/player/web/playurl` |
| BiliHttpApi | `getPgcVideoPlayUrlV2` | GET | `/pgc/player/web/v2/playurl` |
| BiliHttpApi | `getDanmakuXml` | GET | `/x/v1/dm/list.so` |
| BiliHttpApi | `getDynamicList` | GET | `/x/polymer/web-dynamic/v1/feed/all` |
| BiliHttpApi | `getUserInfo` | GET | `/x/space/wbi/acc/info` |
| BiliHttpApi | `getUserCardInfo` | GET | `/x/web-interface/card` |
| BiliHttpApi | `getUserSelfInfo` | GET | `/x/space/myinfo` |
| BiliHttpApi | `getHistories` | GET | `/x/web-interface/history/cursor` |
| BiliHttpApi | `getToView` | GET | `/x/v2/history/toview` |
| BiliHttpApi | `addToView` | POST | `/x/v2/history/toview/add` |
| BiliHttpApi | `addToViewWithAccessKey` | POST | `/x/v2/history/toview/add` |
| BiliHttpApi | `delToView` | POST | `/x/v2/history/toview/del` |
| BiliHttpApi | `delToViewWithAccessKey` | POST | `/x/v2/history/toview/del` |
| BiliHttpApi | `getRelatedVideos` | GET | `/x/web-interface/archive/related` |
| BiliHttpApi | `getFavoriteFolderInfo` | GET | `/x/v3/fav/folder/info` |
| BiliHttpApi | `getAllFavoriteFoldersInfo` | GET | `/x/v3/fav/folder/created/list-all` |
| BiliHttpApi | `getFavoriteList` | GET | `/x/v3/fav/resource/list` |
| BiliHttpApi | `getFavoriteIdList` | GET | `/x/v3/fav/resource/ids` |
| BiliHttpApi | `sendHeartbeat` | POST | `/x/click-interface/web/heartbeat` |
| BiliHttpApi | `sendHeartbeat` | POST | `/x/v2/history/report` |
| BiliHttpApi | `getVideoMoreInfo` | GET | `/x/player/wbi/v2` |
| BiliHttpApi | `sendVideoLike` | POST | `/x/web-interface/archive/like` |
| BiliHttpApi | `checkVideoLiked` | GET | `/x/web-interface/archive/has/like` |
| BiliHttpApi | `sendVideoCoin` | POST | `/x/web-interface/coin/add` |
| BiliHttpApi | `checkVideoSentCoin` | GET | `/x/web-interface/archive/coins` |
| BiliHttpApi | `setVideoToFavorite` | POST | `/x/v3/fav/resource/deal` |
| BiliHttpApi | `checkVideoFavoured` | GET | `/x/v2/fav/video/favoured` |
| BiliHttpApi | `sendVideoOneClickTripleAction` | POST | `/x/web-interface/archive/like/triple` |
| BiliHttpApi | `getWebUserSpaceVideos` | GET | `/x/space/wbi/arc/search` |
| BiliHttpApi | `getAppUserSpaceVideos` | GET | `https://app.bilibili.com/x/v2/space/archive/cursor` |
| BiliHttpApi | `getWebSeasonInfo` | GET | `/pgc/view/web/season` |
| BiliHttpApi | `getAppSeasonInfo` | GET | `/pgc/view/v2/app/season` |
| BiliHttpApi | `addSeasonFollow` | POST | `/pgc/web/follow/add` |
| BiliHttpApi | `addSeasonFollow` | POST | `/pgc/app/follow/add` |
| BiliHttpApi | `delSeasonFollow` | POST | `/pgc/web/follow/del` |
| BiliHttpApi | `delSeasonFollow` | POST | `/pgc/app/follow/del` |
| BiliHttpApi | `getSeasonUserStatus` | GET | `/pgc/view/web/season/user/status` |
| BiliHttpApi | `getVideoTags` | GET | `/x/tag/archive/tags` |
| BiliHttpApi | `getTagDetail` | GET | `/x/tag/detail` |
| BiliHttpApi | `getTagTopVideos` | GET | `/x/web-interface/tag/top` |
| BiliHttpApi | `getTimeline` | GET | `/pgc/web/timeline` |
| BiliHttpApi | `getTimeline` | GET | `/pgc/app/timeline` |
| BiliHttpApi | `getUserFollow` | GET | `/x/relation/followings` |
| BiliHttpApi | `modifyFollow` | POST | `/x/relation/modify` |
| BiliHttpApi | `getRelations` | GET | `/x/space/wbi/acc/relation` |
| BiliHttpApi | `getRelationStat` | GET | `x/relation/stat` |
| BiliHttpApi | `getWebSearchSquare` | GET | `/x/web-interface/wbi/search/square` |
| BiliHttpApi | `getAppSearchSquare` | GET | `https://app.bilibili.com/x/v2/search/square` |
| BiliHttpApi | `getSearchTrendRank` | GET | `https://app.bilibili.com/x/v2/search/trending/ranking` |
| BiliHttpApi | `getKeywordSuggest` | GET | `https://s.search.bilibili.com/main/suggest` |
| BiliHttpApi | `searchAll` | GET | `/x/web-interface/wbi/search/all/v2` |
| BiliHttpApi | `searchType` | GET | `/x/web-interface/wbi/search/type` |
| BiliHttpApi | `getPgcWebInitialStateData` | GET | `https://www.bilibili.com/$path` |
| BiliHttpApi | `getPgcFeedV3` | GET | `/pgc/page/web/v3/feed` |
| BiliHttpApi | `getPgcFeed` | GET | `/pgc/page/web/feed` |
| BiliHttpApi | `getFollowingSeasons` | GET | `/x/space/bangumi/follow/list` |
| BiliHttpApi | `getFollowingSeasons` | GET | `/pgc/app/follow/v2/$type` |
| BiliHttpApi | `getWebInterfaceNav` | GET | `/x/web-interface/nav` |
| BiliHttpApi | `getFeedRcmd` | GET | `/x/web-interface/wbi/index/top/feed/rcmd` |
| BiliHttpApi | `getFeedIndex` | GET | `https://app.bilibili.com/x/v2/feed/index` |
| BiliHttpApi | `seasonIndexResult` | GET | `/pgc/season/index/result` |
| BiliHttpApi | `getWebVideoShot` | GET | `/x/player/videoshot` |
| BiliHttpApi | `getAppVideoShot` | GET | `https://app.bilibili.com/x/v2/view/video/shot` |
| BiliHttpApi | `getUserEquippedGarb` | GET | `/x/garb/user/equip` |
| BiliHttpApi | `getRegionDynamic` | GET | `https://app.bilibili.com/x/v2/region/dynamic` |
| BiliHttpApi | `getRegionDynamicList` | GET | `https://app.bilibili.com/x/v2/region/dynamic/list` |
| BiliHttpApi | `getLocs` | GET | `/x/web-show/res/locs` |
| BiliHttpApi | `getRegionFeedRcmd` | GET | `/x/web-interface/region/feed/rcmd` |
| BiliHttpProxyApi | `getPgcVideoPlayUrl` | GET | `/pgc/player/web/playurl` |
| BiliHttpProxyApi | `getPgcVideoPlayUrlV2` | GET | `/pgc/player/web/v2/playurl` |
| BiliHttpProxyApi | `searchType` | GET | `/x/web-interface/wbi/search/type` |
| BiliLiveHttpApi | `getLiveDanmuInfo` | GET | `/xlive/web-room/v1/index/getDanmuInfo` |
| BiliLiveHttpApi | `getLiveRoomPlayInfo` | GET | `/xlive/web-room/v1/index/getRoomPlayInfo` |
| BiliLiveHttpApi | `getLiveDanmuHistory` | GET | `/xlive/web-room/v1/dM/gethistory` |
| BiliPassportHttpApi | `getWebQRUrl` | GET | `/x/passport-login/web/qrcode/generate` |
| BiliPassportHttpApi | `loginWithWebQR` | GET | `/x/passport-login/web/qrcode/poll` |
| BiliPassportHttpApi | `getAppQRUrl` | POST | `/x/passport-tv-login/qrcode/auth_code` |
| BiliPassportHttpApi | `loginWithAppQR` | POST | `/x/passport-tv-login/qrcode/poll` |
| BiliPassportHttpApi | `getCaptcha` | GET | `/x/passport-login/captcha` |
| BiliPassportHttpApi | `sendSms` | POST | `/x/passport-login/sms/send` |
| BiliPassportHttpApi | `loginWithSms` | POST | `/x/passport-login/login/sms` |
| BiliPlusHttpApi | `view` | GET | `/api/view` |
