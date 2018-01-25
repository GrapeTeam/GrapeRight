package interfaceApplication;

import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.mongodb.util.JSON;

import common.java.JGrapeSystem.rMsg;
import common.java.apps.appsProxy;
import common.java.cache.CacheHelper;
import common.java.check.checkHelper;
import common.java.interfaceModel.GrapeDBSpecField;
import common.java.interfaceModel.GrapeTreeDBModel;
import common.java.nlogger.nlogger;
import common.java.security.codec;
import common.java.session.session;
import common.java.string.StringHelper;
import common.java.time.TimeHelper;

/**
 * 权利内容信息，即（会议，决议，结果信息）
 * 
 *
 */
public class RightContent {
    private GrapeTreeDBModel gDbModel;
    private session se;
    private JSONObject userInfo = null;
    private String currentWeb = null;
    private String pkString;

    private GrapeTreeDBModel _db() {
        GrapeTreeDBModel db = new GrapeTreeDBModel();
        GrapeDBSpecField gdb = new GrapeDBSpecField("userType", "userName", "fatherid", "powerVal");
        gdb.importDescription(appsProxy.tableConfig("RightContent"));
        db.descriptionModel(gdb);
        return db;
    }

    public RightContent() {
        gDbModel = _db();
        pkString = gDbModel.getPk();

        se = new session();
        userInfo = se.getDatas();
        if (userInfo != null && userInfo.size() != 0) {
            currentWeb = userInfo.getString("currentWeb"); // 当前用户所属网站id
        }
    }

    /**
     * 根据权利id，删除所有的会议，决议id
     * 
     * @param rid
     * @return
     */
    public void DeleteIDByRid(String[] rid) {
        for (String string : rid) {
            JSONObject tempjson = gDbModel.eq("fatherid", string).field(pkString).getAllChildren();
            // 获取所有的会议id，决议id
            String id = getID(tempjson);
            // 删除
            delete(id);
        }
    }

    /**
     * 获取子节点所有的会议id，决议id
     * 
     * @return
     */
    private String getID(JSONObject tempjson) {
        String tempID, id = "";
        JSONArray tempArray;
        if (tempjson != null && tempjson.size() > 0) {
            id = tempjson.getString(pkString);
            if (tempjson.containsKey("itemChildrenData")) {
                tempArray = tempjson.getJsonArray("itemChildrenData");
                if (tempArray != null && tempArray.size() > 0) {
                    for (Object object2 : tempArray) {
                        tempjson = (JSONObject) object2;
                        tempID = getID(tempjson);
                        if (StringHelper.InvaildString(id) && !id.equals("0")) {
                            if (StringHelper.InvaildString(tempID) && !id.contains(tempID)) {
                                id = id + "," + tempID;
                            }
                        }
                    }
                }
            }
        }
        return id;
    }

    /**
     * 追加内容数据
     * 
     * @project GrapeContent
     * @package interfaceApplication
     * @file Content.java
     * 
     * @param id
     * @param info
     * @return
     *
     */
    @SuppressWarnings("unchecked")
    public String AddAppend(String id, String info) {
        int code = 99;
        String result = rMsg.netMSG(100, "追加文档失败");
        String contents = "", oldcontent;
        JSONObject object = gDbModel.eq("_id", id).find();
        JSONObject obj = JSONObject.toJSON(info);
        if (obj != null && obj.size() != 0) {
            if (obj.containsKey("content")) {
                contents = obj.getString("content");
                contents = codec.DecodeHtmlTag(contents);
                contents = codec.decodebase64(contents);
                oldcontent = object.getString("content");
                oldcontent += obj.getString("content");
                obj.put("content", oldcontent);
            }
            code = gDbModel.eq("_id", id).data(obj).update() != null ? 0 : 99;
        }
        return code == 0 ? rMsg.netMSG(0, "追加文档成功") : result;
    }

    /**
     * 新增会议、决议操作
     * 
     * @param object
     * @return
     */
    @SuppressWarnings("unchecked")
    protected String NewAdd(JSONObject object) {
        Object obj = null;
        String type = "0";
        if (object != null && object.size() > 0) {
            // 库中不存在当前数据，即新增
            if (object.containsKey("type")) {
                type = object.getString("type");
            }
            switch (type) {
            case "0": // 添加会议
                // 验证当前权利已存在会议，不存在则添加
                if (getMeeting(object)) { // 当前权利下已存在会议信息
                    return rMsg.netMSG(false, "每条权利仅可以添加一条会议信息");
                }
                break;
            case "1": // 添加决议
                // 验证生效时间字段是否必填
                if (CheckEffectiveTime(object)) {
                    return rMsg.netMSG(false, "当前决议生效时间未设置");
                }
                break;

            case "2": // 添加执行结果
                // 验证所属决议是否生效，未生效，则无法添加执行结果
                String data = CheckEffective(object);
                if (data.contains("errorcode")) {
                    if (JSONObject.toJSON(data).getLong("errorcode") != 0) {
                        return data;
                    }
                }
                break;
            case "4": // 添加历史权利修改原因
                // 验证当前权利已存在会议，不存在则添加
                if (getMeeting(object)) { // 当前权利下已存在会议信息
                    return rMsg.netMSG(false, "每条权利仅可以添加一条会议信息");
                }
                break;
            }
            // 新增数据至库中
            object.put("time", TimeHelper.nowMillis()); // 新增时间
            object.put("editTime", 0); // 修改时间
            if (!object.containsKey("wbid")) {
                object.put("wbid", currentWeb);
            }
            obj = gDbModel.data(object).autoComplete().insertOnce();
        }
        return (String) obj;
    }

    /**
     * 新增权利内容信息，即新增会议，决议，执行结果信息
     * 
     * @param info
     *            base64+特殊格式 编码后的数据
     * @return 新增失败：{"message":null,"errorcode":1}
     *         新增成功：{"message":_id,"errorcode":0}
     * 
     */
    @SuppressWarnings("unchecked")
    public String insert(String info) {
        String obj = null;
        String fid = "";
        // 参数解码，转换为JSONObject
        JSONObject object = JSONObject.toJSON(codec.DecodeFastJSON(info));
        if (object != null && object.size() > 0) {
            if (object.containsKey("fatherid")) {
                fid = object.getString("fatherid");
            }
            object.put("itemfatherID", fid);
        }
        obj = NewAdd(object);
        if (StringHelper.InvaildString(obj)) {
            if (obj.contains("errorcode")) {
                return obj;
            }
        }
        return rMsg.netMSG(obj != null, obj);
    }

    /**
     * 修改操作
     * 
     * @param id
     * @param info
     * @return
     */
    @SuppressWarnings("unchecked")
    protected String update(String id, String info) {
        JSONObject rJson = null;
        JSONObject object = JSONObject.toJSON(info);
        if (object != null && object.size() > 0) {
            object.put("editTime", TimeHelper.nowMillis()); // 添加修改时间
            rJson = gDbModel.eq(pkString, id).data(object).update();
        }
        return rMsg.netState(rJson != null);
    }

    /**
     * 修改会议，决议，执行结果
     * 
     * @param id
     * @param info
     * @return
     */
    @SuppressWarnings("unchecked")
    public String updateOne(String id, String info) {
        if (!StringHelper.InvaildString(id)) {
            return rMsg.netMSG(false, "无效id");
        }
        // 参数解码，转换为JSONObject
        JSONObject object = JSONObject.toJSON(codec.DecodeFastJSON(info));
        if (object == null || object.size() < 0) {
            return rMsg.netMSG(false, "非法参数");
        }
        // 验证数据是否已存在，防止重复添加
        String tip = CheckExist(object);
        if (JSONObject.toJSON(tip).getLong("errorcode") != 0) {
            return tip;
        }
        object.put("editTime", TimeHelper.nowMillis()); // 添加修改时间
        JSONObject rJson = gDbModel.eq(pkString, id).data(object).update();
        return rMsg.netState(rJson != null);
    }

    /**
     * 删除会议，决议，执行结果
     * 
     * @param ids
     *            id组，使用“,”分隔
     * @return 删除成功：{"message":删除成功数据量,"errorcode":0}
     *         删除失败：{"message":-1,"errorcode":1}
     */
    public String delete(String ids) {
        long rl = -1;
        boolean rb = true;
        // 获取数据库类型。mongodb数据库，对_id进行ObjectId验证，其他数据库则是数字验证
        int type = getDatabaseType();
        String[] value = ids.split(",");
        int l = value.length;
        if (l > 0) {
            for (String id : value) {
                if (type == 1) {
                    if (ObjectId.isValid(id)) {
                        gDbModel.or().eq(pkString, id);
                    }
                } else {
                    if (checkHelper.isInt(id)) {
                        gDbModel.or().eq(pkString, id);
                    }
                }
            }
            if (gDbModel.getCondCount() > 0) {
                rl = rb ? gDbModel.deleteAll() : -1;
            }
        } else {
            rb = false;
        }
        return rMsg.netMSG(rb, rl);
    }

    /**
     * 获取当前权利下会议，决议，执行结果信息
     * 
     * @param rid
     * @return
     */
    @SuppressWarnings("unchecked")
    public String get(String rid) {
        String linkId = "";
        if (!StringHelper.InvaildString(rid)) {
            return rMsg.netMSG(false, "无效id");
        }
        JSONObject tempjson = gDbModel.eq(pkString, rid).field(pkString + ",name,content,author,souce,time,linkId,mainName").find();
        if (tempjson != null && tempjson.size() > 0) {
            linkId = tempjson.getString("linkId");
        }
        if (StringHelper.InvaildString(linkId)) {
            JSONObject object = getLinkInfo(linkId);
            if (object != null && object.size() > 0) {
                tempjson.put("name", object.getString("mainName"));
                tempjson.put("content", object.getString("content"));
                tempjson.put("author", object.getString("author"));
                tempjson.put("souce", object.getString("souce"));
                tempjson.put("time", object.getLong("time"));
            }
        } else {
            tempjson.put("name", tempjson.getString("mainName"));
        }
        tempjson.remove("mainName");
        tempjson.remove("linkId");
        return rMsg.netMSG(true, tempjson);
    }

    /**
     * 获取当前权利下所有的会议,不填充任何信息
     * 
     * @param rid
     * @return {"Meeting":{},"resolution":[]}
     */
    @SuppressWarnings("unchecked")
    public JSONObject getInfoByID(String rid) {
        String type = "0";
        JSONArray array = new JSONArray();
        JSONObject temp, rJsonObject = new JSONObject();
        JSONObject robj = gDbModel.eq("fatherid", rid).field("wbid,effectiveTime,runCycle,name,fatherid,time,linkId,type,content,author,souce,image,desp").getAllChildren();
        if (robj != null && robj.size() > 0) {
            if (robj.containsKey("type")) {
                type = robj.getString("type");
            }
            JSONArray firArray = robj.getJsonArray("itemChildrenData");
            if (type.equals("3")) {  //存在修改原因
                if (firArray!=null && firArray.size() > 0) {
                    robj = (JSONObject) firArray.get(0);
                }
            }
            robj.remove("_id");
            for (Object object : firArray) {
                temp = (JSONObject) object; // 原来的决议数据
                temp.remove("itemChildrenData"); // 删除结果信息
                temp.remove("_id");
                array.add(temp);
            }
            robj.remove("itemChildrenData");
            rJsonObject.put("Meeting", robj); // 会议信息
            rJsonObject.put("resolution", array); // 决议信息
        }
        return rJsonObject;
    }

    /**
     * 获取当前权利下所有的会议信息，所有决议信息
     * 
     * @param rid
     *            权利id
     * @return 当前权利下的会议，决议信息，详细数据
     */
    public String getInfo(String rid) {
        JSONObject tempJson = null;
        if (!StringHelper.InvaildString(rid)) {
            return rMsg.netMSG(false, "无效权利");
        }
        JSONObject robj = gDbModel.eq("fatherid", rid).field("wbid,effectiveTime,runCycle,name,fatherid,time,linkId,type,editReason").getAllChildren();
        ;
        if (robj != null && robj.size() > 0) {
            // 获取引用文章的文章id
            String linkids = getId(robj);
            tempJson = getLinkArticle(linkids); // 获取链接文章信息
            tempJson = FillName(robj, tempJson); // 将链接文章信息填充至json
        }
        return rMsg.netMSG(true, tempJson);
    }

    /**
     * 获取关联文章信息
     * 
     * @param robj
     * @return
     */
    private JSONObject getLinkArticle(String linkids) {
        // 获取引用文章的文章id
        JSONObject tempJson = null;
        if (StringHelper.InvaildString(linkids)) {
            String Article = (String) appsProxy.proxyCall("/GrapeContent/Content/findAllArticles/" + linkids);
            tempJson = JSONObject.toJSON(Article);
        }
        return tempJson;
    }

    /**
     * 引用文章，填充名称
     * 
     * @param object
     * @param ArticleJson
     * @return
     */
    @SuppressWarnings("unchecked")
    private JSONObject FillName(JSONObject object, JSONObject ArticleJson) {
        String tempID = "";
        JSONArray tempArray = new JSONArray();
        JSONObject tempjson = new JSONObject(), Article;
        if (object != null && object.size() > 0) {
            if (ArticleJson != null && ArticleJson.size() > 0) {
                tempID = object.getString("linkId");
                Article = ArticleJson.getJson(tempID);
                if (Article != null && Article.size() > 0) {
                    object.put("name", Article.getString("mainName"));
                    if (object.containsKey("itemChildrenData")) {
                        tempArray = object.getJsonArray("itemChildrenData");
                        if (tempArray != null && tempArray.size() > 0) {
                            for (Object object2 : tempArray) {
                                tempjson = (JSONObject) object2;
                                FillName(tempjson, ArticleJson);
                            }
                        }
                    }
                }
            }
        }
        return object;
    }

    /**
     * 获取所有链接id
     * 
     * @param object
     * @return
     */
    private String getId(JSONObject object) {
        String LinkId = "", tempID;
        JSONArray tempArray = new JSONArray();
        JSONObject tempjson = new JSONObject();
        if (object != null && object.size() > 0) {
            LinkId = object.getString("linkId");
            if (object.containsKey("itemChildrenData")) {
                tempArray = object.getJsonArray("itemChildrenData");
                if (tempArray != null && tempArray.size() > 0) {
                    for (Object object2 : tempArray) {
                        tempjson = (JSONObject) object2;
                        tempID = getId(tempjson);
                        if (StringHelper.InvaildString(LinkId) && !LinkId.equals("0")) {
                            if (StringHelper.InvaildString(tempID) && !LinkId.contains(tempID)) {
                                LinkId = LinkId + "," + tempID;
                            }
                        }
                    }
                }
            }
        }
        return LinkId;
    }

    /**
     * 获取引用文章信息
     * 
     * @param linkId
     * @return
     */
    private JSONObject getLinkInfo(String linkId) {
        String key = "linkInfo_" + linkId;
        CacheHelper ch = new CacheHelper();
        if (ch.get(key) != null) {
            return JSONObject.toJSON(ch.get(key));
        }
        JSONObject object = null;
        if (StringHelper.InvaildString(linkId)) {
            String tempInfo = (String) appsProxy.proxyCall("/GrapeContent/Content/findArticle/" + linkId);
            object = JSONObject.toJSON(tempInfo);
            object = JSONObject.toJSON(object.getString("message"));
            ch.setget(key, object, 10 * 60);
        }
        return object;
    }

    /**
     * 按条件分页获取执行结果信息数据
     * 
     * @param idx
     * @param pageSize
     * @param condString
     * @return
     */
    public String pageby(int idx, int pageSize, String condString) {
        String out = null;
        JSONArray array = null;
        if (idx <= 0) {
            return rMsg.netMSG(false, "页码错误");
        }
        if (pageSize <= 0) {
            return rMsg.netMSG(false, "页长度错误");
        }
        JSONArray condObj = JSONArray.toJSONArray(condString);
        if (condObj != null) {
            gDbModel.where(condObj).desc("time").desc(pkString);
            array = gDbModel.dirty().page(idx, pageSize);
            out = rMsg.netPAGE(idx, pageSize, gDbModel.count(), FillArticle(array));
        } else {
            out = rMsg.netMSG(false, "无效条件");
        }
        return out;
    }

    /**
     * 填充文章名称
     * 
     * @param array
     * @return
     */
    @SuppressWarnings("unchecked")
    private JSONArray FillArticle(JSONArray array) {
        String linkID = "", tempID;
        JSONObject tempJSON;
        if (array != null && array.size() > 0) {
            int l = array.size();
            for (Object object : array) {
                tempJSON = (JSONObject) object;
                tempID = getId(tempJSON);
                if (StringHelper.InvaildString(tempID) && !linkID.contains(tempID)) {
                    linkID += tempID + ",";
                }
            }
            JSONObject ArticleInfo = getLinkArticle(StringHelper.fixString(linkID, ','));
            for (int i = 0; i < l; i++) {
                tempJSON = (JSONObject) array.get(i);
                array.set(i, FillName(tempJSON, ArticleInfo));
            }
        }
        return array;
    }

    /**
     * 验证当前权利是否已存在会议
     * 
     * @param object
     * @return
     */
    private boolean getMeeting(JSONObject object) {
        String rid = "";
        JSONObject obj = null;
        if (object != null && object.size() > 0) {
            rid = object.getString("fatherid");
        }
        if (StringHelper.InvaildString(rid)) {
            obj = gDbModel.eq("fatherid", rid).find();
        }
        return obj != null && obj.size() > 0;
    }

    /**
     * 验证当前决议生效时间字段是否填写
     * 
     * @param object
     * @return
     */
    private boolean CheckEffectiveTime(JSONObject object) {
        long EffectiveTime = 0;
        try {
            if (object != null && object.size() > 0) {
                if (object.containsKey("effectiveTime")) {
                    EffectiveTime = object.getLong("effectiveTime");
                }
            }
        } catch (Exception e) {
            nlogger.logout(e, "运行时间数据类型错误");
            EffectiveTime = 0;
        }
        return EffectiveTime == 0;
    }

    /**
     * 添加执行结果，验证所属决议是否生效
     * 
     * @param object
     * @return
     */
    private String CheckEffective(JSONObject object) {
        String rid = ""; // 所属决议id
        long EffectiveTime; // 决议生效时间
        String data = object.toJSONString(), eTime = "";
        if (object != null && object.size() > 0) {
            if (object.containsKey("fatherid")) {
                rid = object.getString("fatherid"); // 获取所属决议id
            }
            if (StringHelper.InvaildString(rid)) {
                eTime = getTime(rid);
            }
            if (!eTime.contains("errorcode")) {
                EffectiveTime = (!StringHelper.InvaildString(eTime)) ? 0 : Long.parseLong(eTime); // 获取决议生效时间
                if (EffectiveTime >= TimeHelper.nowMillis()) { // 生效时间大于当前时间，无法添加执行结果
                    data = rMsg.netMSG(false, "该决议还未生效，无法添加执行结果信息");
                }
            }
        }
        return data;
    }

    /**
     * 获取决议生效时间
     * 
     * @param id
     * @return
     */
    private String getTime(String id) {
        String time = "";
        if (!StringHelper.InvaildString(id)) {
            return rMsg.netMSG(false, "无效id");
        }
        JSONObject object = gDbModel.eq(pkString, id).field("effectiveTime").find();
        if (object != null && object.size() > 0) {
            time = object.getString("effectiveTime");
        }
        return time;
    }

    /**
     * 验证数据是否已存在
     * 
     * @param object
     * @return
     */
    private String CheckExist(JSONObject object) {
        String name = ""; // 名称
        String content = ""; // 内容
        String linkid = ""; // 关联文章id
        long type = 0; // 类型
        String wbid = ""; // 所属站点id

        String result = rMsg.netMSG(false, "当前数据已存在");
        JSONObject obj = null;
        if (object != null && object.size() > 0) {
            if (object.containsKey("name")) {
                name = object.getString("name");
            }
            if (object.containsKey("content")) {
                content = object.getString("content");
            }
            if (object.containsKey("linkId")) {
                linkid = object.getString("linkId");
            }
            if (object.containsKey("type")) {
                type = object.getLong("type");
            }
            if (object.containsKey("wbid")) {
                wbid = object.getString("wbid");
            } else {
                wbid = currentWeb;
            }
            // linkid 值不为null或者空，通过name,linkid,wbid验证是否存在
            if (StringHelper.InvaildString(linkid)) {
                gDbModel.eq("linkId", linkid);
            } else if (StringHelper.InvaildString(content)) {
                // content 值不为null或者空，通过name,content,wbid验证是否存在
                gDbModel.eq("content", content);
            } else {
                return rMsg.netState(true);
            }
            obj = gDbModel.eq("wbid", wbid).eq("name", name).eq("type", type).find();
        }
        return (obj != null && obj.size() > 0) ? result : rMsg.netState(true);
    }

    /**
     * 获取数据库类型，对于不同的数据库类型的id，使用不同的验证方法
     * 
     * @return 1：mongodb 2：mysql
     */
    private int getDatabaseType() {
        int i = 0;
        try {
            i = gDbModel._dbName;
        } catch (Exception e) {
            nlogger.logout(e, "配置异常");
            i = 0;
        }
        return i;
    }
}
