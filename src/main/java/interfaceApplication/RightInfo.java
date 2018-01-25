package interfaceApplication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import common.java.JGrapeSystem.rMsg;
import common.java.apps.appsProxy;
import common.java.check.checkHelper;
import common.java.interfaceModel.GrapeDBSpecField;
import common.java.interfaceModel.GrapeTreeDBModel;
import common.java.nlogger.nlogger;
import common.java.security.codec;
import common.java.session.session;
import common.java.string.StringHelper;
import common.java.time.TimeHelper;

/**
 *
 * 权利信息
 *
 */
public class RightInfo {
    private static ExecutorService rs = Executors.newFixedThreadPool(300);
    private GrapeTreeDBModel gDbModel;
    private session se;
    private JSONObject userInfo = null;
    private String currentWeb = null;
    private String pkString;

    private GrapeTreeDBModel _db() {
        GrapeTreeDBModel db = new GrapeTreeDBModel();
        GrapeDBSpecField gdb = new GrapeDBSpecField("userType", "userName", "fatherid", "powerVal");
        gdb.importDescription(appsProxy.tableConfig("RightInfo"));
        db.descriptionModel(gdb);
        return db;
    }

    public RightInfo() {
        gDbModel = _db();
        pkString = gDbModel.getPk();

        se = new session();
        userInfo = se.getDatas();
        if (userInfo != null && userInfo.size() != 0) {
            currentWeb = userInfo.getString("currentWeb"); // 当前用户所属网站id
        }
    }

    /**
     * 获取历史权利信息
     * 
     * @param info
     * @return
     */
    public String getHistory(String rid) {
        JSONArray array = new JSONArray();
        array = gDbModel.eq("fatherid", rid).select();
        return rMsg.netMSG(true, array);
    }

    /**
     * 新增权利历史(更新权利信息)
     * 
     * @param info
     * @return
     */
    public String AddHistory(String info) {
        String temp = "";
        int isCopy = 0; // 默认不复制
        // 参数解码，转换为JSONObject
        JSONObject object = JSONObject.toJSON(codec.DecodeFastJSON(info));
        if (object == null || object.size() <= 0) {
            return rMsg.netMSG(false, "参数不合法");
        }
        if (object.containsKey("isCopy")) {
            temp = object.getString("isCopy");
            if (StringHelper.InvaildString(temp) && checkHelper.isInt(temp)) {
                isCopy = Integer.parseInt(temp);
            }
        }
        object.remove("isCopy");
        return AddHistory(isCopy, object);
    }

    /**
     * 更新权利数据
     * 
     * @param isCopy
     *            0：不复制数据；1：复制数据
     * @param object
     * @return
     */
    @SuppressWarnings("unchecked")
    private String AddHistory(int isCopy, JSONObject object) {
        String reason = "",oldID = "", fatherid = "", result = rMsg.netState(false);
        // 若isCopy为1，则先查询该权利下的会议，决议数据，再新增数据，若isCopy为0，则直接添加数据
        switch (isCopy) {
        case 1: // 复制数据
            // 根据id获取原数据
            if (object.containsKey(pkString)) {
                oldID = object.getString(pkString); // 原权利数据的id
            }
            if (object.containsKey("fatherid")) {
                fatherid = object.getString("fatherid"); // 更新原因
            }
            if (object.containsKey("reason")) {
                reason = object.getString("reason");
            }
            if (StringHelper.InvaildString(oldID) && ObjectId.isValid(oldID)) { // 查询当前权利下会议，决议信息
                // 新增会议，决议信息
                return AddRight(oldID, fatherid, reason);
            } else {
                return rMsg.netMSG(false, "无效权利id，无法复制权利信息");
            }
        default:
            // 直接新增权利
            object.put("editTime", TimeHelper.nowMillis());
            if (object.containsKey("children")) { // 新增权利的同时，新增会议，决议，信息
                result = Add(codec.encodeFastJSON(object.toJSONString()));
            } else {
                if (object.containsKey("_id")) {
                    return rMsg.netMSG(false, "参数异常，新增失败");
                }
                result = insert(object.toJSONString());
            }
            break;
        }
        return result;
    }

    /**
     * 复制当前权利信息，包含所包含的会议，决议信息， 查询原权利信息，新增信息
     * 通过原权利id查询会议，决议信息，封装数据为{"Meeting":{},"resolution":[]},Meeting：表示所包含的会议信息，1
     * ：表示所包含的决议信息
     * 
     * @param rid
     *            原权利id
     * @param fatherid
     *            父权利id
     * @param reasonObj
     *            修改原因
     * @return
     */
    @SuppressWarnings("unchecked")
    private String AddRight(String rid, String fatherid, String reason) {
        RightContent rContent = new RightContent();
        String newID = ""; // 新权利id
        String newresID = "0"; // 新的决议id
        JSONObject meetObj, temp;
        if (StringHelper.InvaildString(rid) && ObjectId.isValid(rid)) {
            JSONObject object = gDbModel.eq(pkString, rid).find();
            if (object != null && object.size() > 0) {
                object.remove("_id");
                object.put("fatherid", fatherid); // 为哪一条权利新增历史权利
                object.put("itemfatherID", fatherid); // 为哪一条权利新增历史权利
                object.put("editTime", TimeHelper.nowMillis()); // 更新时间
                object.put("editReason", reason); // 更新原因
                newID = (String) gDbModel.data(object).autoComplete().insertOnce();
            }
            // 通过原权利id查询所包含的会议决议信息
            JSONObject obj = rContent.getInfoByID(rid);
            if (obj != null && obj.size() > 0) {
                meetObj = obj.getJson("Meeting"); // 获取会议信息
                if (meetObj != null && meetObj.size() > 0) {
                    meetObj.put("fatherid", newID);// fatherid修改为新的原因id
                    meetObj.put("itemfatherID", newID);
                    String meetingID = new RightContent().NewAdd(meetObj); // 添加会议信息
                    if (!StringHelper.InvaildString(meetingID)) {
                        String newRid = newID;
                        // 添加失败，则删除新复制的权利信息
                        rs.execute(() -> {
                            delete(newRid);
                        });
                        return rMsg.netMSG(false, "更新权利失败");
                    }
                    // 获取决议信息
                    JSONArray array = obj.getJsonArray("resolution");
                    for (Object object2 : array) {
                        temp = (JSONObject) object2;
                        temp.put("fatherid", meetingID);// fatherid修改为新的会议id
                        temp.put("itemfatherID", meetingID);
                        if (StringHelper.InvaildString(newresID)) {
                            newresID = rContent.NewAdd(temp);
                        } else {
                            String newRid = newID;
                            // 添加失败，则删除新复制的权利,会议信息
                            rs.execute(() -> {
                                deletes(newRid, meetingID);
                            });
                            return rMsg.netMSG(false, "更新权利失败");
                        }
                    }
                }
            }
        }
        return rMsg.netState(true);
    }

    /**
     * 新增权利信息,同时新增会议，决议信息
     * 
     * @param info
     *            {"name":"权利名称","children":{"name":"会议名称","children":{"name":
     *            "决议名称"}}} base64+特殊格式 编码后的数据
     * @return 新增失败：{"message":null,"errorcode":1}
     *         新增成功：{"message":_id,"errorcode":0}
     * 
     *         新增成功，返回决议id
     * 
     */
    public String Add(String info) {
        String wbid = "", rightid = "", meetid = "", fid = "", reasonId = "";
        // 参数解码，转换为JSONObject
        JSONObject object = JSONObject.toJSON(codec.DecodeFastJSON(info));
        JSONObject Meeting = object.getJson("children"); // 获取新增的会议信息
        JSONObject resolution = Meeting.getJson("children"); // 获取新增的决议信息
        object.remove("children"); // 获取新增的权利信息

        if (object.containsKey("wbid")) {
            wbid = object.getString("wbid");
        }
        // 新增权利操作
        rightid = NewAdd(object);
        if (!StringHelper.InvaildString(rightid)) {
            return rMsg.netMSG(false, "新增权利失败");
        }
        if (rightid.contains("errorcode")) {
            return rightid;
        }
        if (Meeting != null && Meeting.size() > 0) {
            // 新增会议信息
            Meeting.remove("children"); // 获取新增的会议信息
            meetid = AddMeeting(Meeting, rightid, wbid);
            if (!StringHelper.InvaildString(meetid)) {
                String rid = rightid;
                String reasonid = reasonId;
                rs.execute(() -> {
                    delete(rid);
                    delete(reasonid);
                });
                return rMsg.netMSG(false, "新增会议失败");
            }
            if (meetid.contains("errorcode")) {
                return meetid;
            }
            // 新增决议信息
            fid = AddMeeting(resolution, meetid, wbid);
            if (!StringHelper.InvaildString(fid)) {
                // 删除新增的权利，删除新增的会议
                String rid = rightid;
                String mid = meetid;
                rs.execute(() -> {
                    deletes(rid, mid);
                });
                return rMsg.netMSG(false, "新增决议失败");
            }
            if (fid.contains("errorcode")) {
                return fid;
            }
        }
        return rMsg.netMSG(rightid != null, rightid);
    }

    /**
     * 删除新增的权利，删除新增的会议
     * 
     * @param rightid
     *            权利id
     * @param meetid
     *            会议id
     */
    private void deletes(String rightid, String meetid) {
        RightContent rightContent = new RightContent();
        if (StringHelper.InvaildString(rightid)) {
            delete(rightid);
        }
        if (StringHelper.InvaildString(meetid)) {
            rightContent.delete(meetid);
        }
    }

    /**
     * 新增会议，决议信息
     * 
     * @param meeting
     * @param fid
     * @param wbid
     * @return 添加成功，返回id，否则返回异常消息
     */
    @SuppressWarnings("unchecked")
    private String AddMeeting(JSONObject meeting, String fid, String wbid) {
        String meetid = "";
        if (StringHelper.InvaildString(fid)) {
            meeting.put("fatherid", fid);
            meeting.put("itemfatherID", fid);
            meeting.put("wbid", wbid);
        }
        RightContent rightContent = new RightContent();
        if (meeting.containsKey("linkId")) { // 包含关联文章id，直接添加至数据库
            meetid = rightContent.NewAdd(meeting);
        } else { // 不包含关联文章id，修改数据
            meetid = meeting.getString("existid"); // 会议或者决议id
            meeting.remove("existid");
            rightContent.update(meetid, meeting.toJSONString());
        }
        return meetid;
    }

    /**
     * 权利新增操作
     * 
     * @param object
     * @return
     */
    @SuppressWarnings("unchecked")
    private String NewAdd(JSONObject object) {
        Object obj = null;
        String fatherid = "0";
        if (object != null && object.size() > 0) {
//            String tip = CheckExist(object);
//            if (JSONObject.toJSON(tip).getLong("errorcode") != 0) {
//                return tip;
//            }
            object.put("time", TimeHelper.nowMillis()); // 新增时间
            if (!object.containsKey("editTime")) { // 是否包含修改时间
                object.put("editTime", 0); // 默认设置修改时间为0
                if (object.containsKey("fatherid")) {
                    // 若新增加的权利为一级权利，则不设置修改时间，否则修改时间为当前时间
                    fatherid = object.getString("fatherid");
                    if (StringHelper.InvaildString(fatherid) && !fatherid.equals("0")) {
                        object.put("editTime", TimeHelper.nowMillis());
                    }
                }
            }
            if (!object.containsKey("wbid")) {
                object.put("wbid", currentWeb);
            }
            obj = gDbModel.data(object).autoComplete().insertOnce();
        }
        return (String) obj;
    }

    /**
     * 新增权利信息
     * 
     * @param info
     *            base64+特殊格式 编码后的数据
     * @return 新增失败：{"message":null,"errorcode":1}
     *         新增成功：{"message":_id,"errorcode":0}
     * 
     */
    public String insert(String info) {
        String obj = null;
        // 参数解码，转换为JSONObject
        JSONObject object = JSONObject.toJSON(codec.DecodeFastJSON(info));
        obj = NewAdd(object);
        if (StringHelper.InvaildString(obj)) {
            if (obj.contains("errorcode")) {
                return obj;
            }
        }
        return rMsg.netMSG(obj != null, obj);
    }

    /**
     * 修改权利信息
     * 
     * @param id
     *            id
     * @param info
     *            base64+特殊格式 编码后的数据
     * @return 修改成功：{"message":"","errorcode":0}
     *         修改失败：{"message":"","errorcode":1}
     * 
     */
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
        JSONObject rJson = gDbModel.eq(pkString, id).data(object).update();
        return rMsg.netState(rJson != null);
    }

    /**
     * 删除权利信息，同时删除该权利下的会议，决议，执行结果等信息
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
                // 获取当前权利下的所有会议，决议，执行结果，并删除
                // rs.execute(() -> {
                new RightContent().DeleteIDByRid(value);
                // });
                rl = rb ? gDbModel.deleteAll() : -1;
            }
        } else {
            rb = false;
        }
        return rMsg.netMSG(rb, rl);
    }

    /**
     * 获取 一条权利信息
     * 
     * @param id
     * @return
     */
    public String get(String id) {
        if (!StringHelper.InvaildString(id)) {
            return rMsg.netMSG(false, "无效id");
        }
        JSONObject object = gDbModel.eq(pkString, id).find();
        return rMsg.netMSG(true, getName(object));
    }

    /**
     * 获取当前站点全部权利信息
     * 
     * @return
     */
    public String getAll() {
        JSONArray array = gDbModel.eq("wbid", currentWeb).select();
        return rMsg.netMSG(true, getName(array));
    }

    /**
     * 分页获取权利信息
     * 
     * @param idx
     *            当前页
     * @param pageSize
     *            每页最大数据量
     * @return
     */
    public String page(int idx, int pageSize) {
        if (idx <= 0) {
            return rMsg.netMSG(false, "页码错误");
        }
        if (pageSize <= 0) {
            return rMsg.netMSG(false, "页长度错误");
        }
        JSONArray array = gDbModel.dirty().desc("time").page(idx, pageSize);
        return rMsg.netPAGE(idx, pageSize, gDbModel.count(), getName(array));
    }

    /**
     * 按条件分页获取权利信息
     * 
     * @param idx
     *            当前页
     * @param pageSize
     *            每页最大数据量
     * @param cond
     *            查询条件
     * @return
     */
    public String pageby(int idx, int pageSize, String cond) {
        String out = null;
        if (idx <= 0) {
            return rMsg.netMSG(false, "页码错误");
        }
        if (pageSize <= 0) {
            return rMsg.netMSG(false, "页长度错误");
        }
        JSONArray condObj = JSONArray.toJSONArray(cond);
        if (condObj != null) {
            gDbModel.where(condObj).desc("time").desc(pkString);
            JSONArray array = gDbModel.dirty().page(idx, pageSize);
            out = rMsg.netPAGE(idx, pageSize, gDbModel.count(), getName(array));
        } else {
            out = rMsg.netMSG(false, "无效条件");
        }
        return out;
    }

    /**
     * 填充所属类型
     * 
     * @param array
     * @return
     */
    @SuppressWarnings("unchecked")
    private JSONArray getName(JSONArray array) {
        JSONObject obj;
        String tempID, ID = "";
        if (array != null && array.size() > 0) {
            int l = array.size();
            for (Object object : array) {
                obj = (JSONObject) object;
                tempID = obj.getString("type");
                if (StringHelper.InvaildString(tempID) && !ID.contains(tempID)) {
                    ID += tempID + ",";
                }
            }
            // 查询类型名称
            JSONObject object = new RightType().getName(StringHelper.fixString(ID, ','));
            if (object != null && object.size() > 0) {
                for (int i = 0; i < l; i++) {
                    obj = (JSONObject) array.get(i);
                    array.set(i, fillTypeName(obj, object));
                }
            }
        }
        return array;
    }

    /**
     * 填充所属类型
     * 
     * @param array
     * @return
     */
    private JSONObject getName(JSONObject object) {
        String ID = "";
        ID = object.getString("type");
        // 查询类型名称
        JSONObject obj = new RightType().getName(ID);
        return fillTypeName(object, obj);
    }

    /**
     * 填充类型名称
     * 
     * @param RightInfo
     *            权利信息
     * @param TypeInfo
     *            类型信息
     * @return
     */
    @SuppressWarnings("unchecked")
    private JSONObject fillTypeName(JSONObject RightInfo, JSONObject TypeInfo) {
        String tempID;
        if (RightInfo != null && RightInfo.size() > 0) {
            tempID = RightInfo.getString("type");
            if (TypeInfo != null && TypeInfo.size() > 0) {
                RightInfo.put("typeName", TypeInfo.getString(tempID));
            }
        }
        return RightInfo;
    }

    /**
     * 验证数据是否已存在
     * 
     * @param object
     * @return
     */
    private String CheckExist(JSONObject object) {
        String name = "", wbid = "", type = "0";
        String result = rMsg.netMSG(false, "该权利已存在");
        JSONObject obj = null;
        if (object != null && object.size() > 0) {
            if (!object.containsKey("name")) {
                return rMsg.netMSG(false, "未填写权利名称");
            }
            if (object.containsKey("type")) {
                type = object.getString("type");
            }
            if (object.containsKey("wbid")) {
                wbid = object.getString("wbid");
            } else {
                wbid = currentWeb;
            }
            name = object.getString("name");
            if (!StringHelper.InvaildString(name)) {
                return rMsg.netMSG(false, "权利名称不能为空，请填写正确的名称");
            }
            obj = gDbModel.eq("name", name).eq("wbid", wbid).eq("type", type).eq("isdelete", 0).eq("isvisble", 0).find();
        }
        return (obj != null && obj.size() > 0) ? result : rMsg.netState(true);
    }

    /**
     * 获取数据库类型
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
