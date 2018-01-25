package interfaceApplication;

import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import common.java.JGrapeSystem.rMsg;
import common.java.apps.appsProxy;
import common.java.check.checkHelper;
import common.java.interfaceModel.GrapeDBSpecField;
import common.java.interfaceModel.GrapeTreeDBModel;
import common.java.interfaceType.apiType;
import common.java.interfaceType.apiType.tpye;
import common.java.nlogger.nlogger;
import common.java.security.codec;
import common.java.session.session;
import common.java.string.StringHelper;
import common.java.time.TimeHelper;

/**
 * 权利分类管理
 * 
 *
 */
public class RightType {
    private GrapeTreeDBModel gDbModel;
    private session se;
    private JSONObject userInfo = null;
    private String currentWeb = null;
    private String pkString;

    private GrapeTreeDBModel _db() {
        GrapeTreeDBModel db = new GrapeTreeDBModel();
        GrapeDBSpecField gdb = new GrapeDBSpecField();
        gdb.importDescription(appsProxy.tableConfig("RightType"));
        db.descriptionModel(gdb);
        return db;
    }

    public RightType() {
        gDbModel = _db();
        pkString = gDbModel.getPk();

        se = new session();
        userInfo = se.getDatas();
        if (userInfo != null && userInfo.size() != 0) {
            currentWeb = userInfo.getString("currentWeb"); // 当前用户所属网站id
        }
    }

    /**
     * 新增类型
     * 
     * @param info
     * @return
     */
    @SuppressWarnings("unchecked")
    public String insert(String info) {
        Object obj = null;
        JSONObject object = JSONObject.toJSON(codec.DecodeFastJSON(info));
        if (object == null || object.size() <= 0) {
            return rMsg.netMSG(false, "无效参数");
        }
        if (CheckParam(object)) {
            return rMsg.netMSG(false, "该类型已存在");
        }
        object.put("time", TimeHelper.nowMillis()); // 添加新增时间
        // 不包含wbid字段，则填充当前站点id
        if (!object.containsKey("wbid")) {
            object.put("wbid", currentWeb);
        }
        obj = gDbModel.data(object).autoComplete().insertOnce();
        return rMsg.netMSG(obj != null, obj);
    }

    /**
     * 修改类型
     * 
     * @param id
     *            类型id
     * @param info
     *            待修改kv值
     * @return
     */
    @SuppressWarnings("unchecked")
    public String updateOne(String id, String info) {
        if (!StringHelper.InvaildString(id)) {
            return rMsg.netMSG(false, "无效类型");
        }
        JSONObject object = JSONObject.toJSON(codec.DecodeFastJSON(info));
        if (object == null || object.size() <= 0) {
            return rMsg.netMSG(false, "无效参数");
        }
        if (CheckParam(object)) {
            return rMsg.netMSG(false, "该类型已存在");
        }
        object.put("editTime", TimeHelper.nowMillis()); // 填充修改时间
        JSONObject rObj = gDbModel.eq(pkString, id).data(object).update();
        return rMsg.netState(rObj != null);
    }

    /**
     * 删除分类
     * 
     * @param ids
     *            类型id组，“,”分隔
     * @return
     */
    public String delete(String ids) {
        long rl = -1;
        boolean rb = true;
        String[] value = ids.split(",");
        int l = value.length;
        if (l > 0) {
            for (String id : value) {
                gDbModel.or().eq(pkString, id);
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
     * 分页显示类型信息
     * 
     * @param idx
     * @param pageSize
     * @return
     */
    public String page(int idx, int pageSize) {
        if (idx <= 0) {
            return rMsg.netMSG(false, "页码错误");
        }
        if (pageSize <= 0) {
            return rMsg.netMSG(false, "页长度错误");
        }
        gDbModel.eq("wbid", currentWeb);
        return rMsg.netPAGE(idx, pageSize, gDbModel.dirty().count(), gDbModel.desc("time").page(idx, pageSize));
    }

    /**
     * 按条件分页
     * 
     * @param idx
     * @param pageSize
     * @param cond
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
            out = rMsg.netPAGE(idx, pageSize, gDbModel.dirty().count(), gDbModel.page(idx, pageSize));
        } else {
            out = rMsg.netMSG(false, "无效条件");
        }
        return out;
    }

    /**
     * 查询分类名称，用于显示在权利信息
     * 
     * @param ids
     * @return{typeid1:typename1,typeid2:typename2}
     */
    protected JSONObject getName(String ids) {
        JSONArray array = null;
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
            array = gDbModel.field(pkString + ",name").select();
        }
        return JoinJson(array);
    }

    /**
     * 生成json
     * 
     * @param array
     * @return {id1:name1,id2:name2}
     */
    @SuppressWarnings("unchecked")
    private JSONObject JoinJson(JSONArray array) {
        JSONObject rjson = new JSONObject();
        JSONObject obj;
        if (array != null && array.size() > 0) {
            for (Object object : array) {
                obj = (JSONObject) object;
                rjson.put(obj.getMongoID(pkString), obj.getString("name"));
            }
        }
        return rjson;
    }

    /**
     * 验证增加，修改的类型是否已存在
     * 
     * @param object
     * @return true：该类型已存在；false：不存在
     */
    private boolean CheckParam(JSONObject object) {
        String name = "", wbid = currentWeb, fatherid = "0";
        if (object != null && object.size() > 0) {
            if (object.containsKey("name")) {
                name = object.getString("name");
            }
            if (object.containsKey("fatherid")) {
                fatherid = object.getString("fatherid");
            }
            if (object.containsKey("wbid")) {
                wbid = object.getString("wbid");
            }
        }
        JSONObject obj = gDbModel.eq("name", name).eq("wbid", wbid).eq("fatherid", fatherid).find();
        return (obj != null && obj.size() > 0);
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
