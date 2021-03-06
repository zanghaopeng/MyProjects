/**
 * 
 */
package com.zhp.cloud.note.controller;

import com.zhp.cloud.note.bean.Article;
import com.zhp.cloud.note.bean.Note;
import com.zhp.cloud.note.bean.NoteBook;
import com.zhp.cloud.note.service.NoteService;
import com.zhp.cloud.util.constants.Constants;
import net.sf.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.MappingJacksonJsonView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/note")
public class NoteController {
	private static Logger logger = LoggerFactory
			.getLogger(NoteController.class);
	@Resource
	private NoteService noteService;

	/**
	 *
	 * @Description: 但凡涉及到增删改的都需要一致性:即 Redis 改了 若HBASE更新失败 则Redis也要改为之前的状态
	 *
	 * @auther: 臧浩鹏
	 * @date: 16:44 2018/8/15
	 *
	 */

	/**
	 * 获取指定用户的所有笔记本
	 * 
	 * @param request
	 * @return
	 */
	@RequestMapping("/getAllNoteBook")
	@ResponseBody
	public Model getAllNoteBook(HttpServletRequest request,Model model) {
		String userName = null;
		try {
			// 从session中获取用户名
			userName = (String) request.getSession().getAttribute(
					Constants.USER_INFO);
			// 查询用户笔记本
			// 查询所有笔记本
			List<NoteBook> allNoteBook = noteService.getAllNoteBook(userName);
			// 封装返回值
			ModelMap map = new ModelMap();
			map.put("allNoteBook", allNoteBook);
			map.put("recycleBtRowKey", userName + Constants.RECYCLE);
			map.put("starBtRowKey", userName + Constants.STAR);
			map.put("activityBtRowKey", userName + Constants.ACTIVITY);
			model.addAttribute(map);
		} catch (Exception e) {
			logger.error("用户" + userName
					+ "获取所有笔记本异常|方法:getAllNoteBook|参数： userName:" + userName, e);
			e.printStackTrace();
		}
		return model;
	}

	/**
	 * 获取指定用户的所有笔记本
	 * 
	 * @param request
	 * @return
	 */
	@RequestMapping("/getAllNoteBookByUserName")
	public ModelAndView getAllNoteBookByUserName(HttpServletRequest request,
			String userName) {
		ModelAndView modelAndView = null;
		try {
			request.getSession().setAttribute(Constants.USER_INFO, userName);
			// 查询所有笔记本
			List<NoteBook> allNoteBook = noteService.getAllNoteBook(userName);
			ModelMap map = new ModelMap();
			map.put("allNoteBook", allNoteBook);
			map.put("recycleBtRowKey", userName + Constants.RECYCLE);
			map.put("starBtRowKey", userName + Constants.STAR);
			map.put("activityBtRowKey", userName + Constants.ACTIVITY);
			modelAndView = new ModelAndView(new MappingJacksonJsonView(), map);
		} catch (Exception e) {
			logger.error("用户" + userName
					+ "获取所有笔记本异常|方法:getAllNoteBookByUserName|参数： userName:"
					+ userName, e);
			e.printStackTrace();
		}
		return modelAndView;
	}

	/**
	 * 添加笔记本:先往Redis中添加再向HBASE中添加;如果hbase添加失败 那么将Redis中的数据删除;
	 * notebook: rowKey = userName.trim() + Constants.ROWKEY_SEPARATOR + createTime.trim(); 用户名_创建时间
	 * 创建一个二维数组 用于装载 put()所需的参数:列族,列名,列值
	 * @param request
	 * @param noteBookName :笔记本名字
	 * @return
	 */
	@RequestMapping("/addNoteBook")
	public ModelAndView addNoteBook(HttpServletRequest request,
			String noteBookName) {
		ModelAndView modelAndView = null;
		// 从session中获取用户循序
		String userName = (String) request.getSession().getAttribute(
				Constants.USER_INFO);
		try {
			// 创建时间戳
			Long createTime = System.currentTimeMillis();
			// 保存笔记本
			boolean b = noteService.addNoteBook(noteBookName, userName,
					createTime.toString(), 0);
			ModelMap map = new ModelMap();
			if (b) {
				// 封装rowkey信息返回前台
				map.put("resource", userName + Constants.ROWKEY_SEPARATOR
						+ createTime);
				map.put("success", true);
			} else {
				map.put("success", false);
			}
			modelAndView = new ModelAndView(new MappingJacksonJsonView(), map);

		} catch (Exception e) {
			logger.error("用户" + userName
					+ "添加笔记本异常|方法：addNoteBook|  参数：noteBookName:"
					+ noteBookName, e);
			e.printStackTrace();
		}
		return modelAndView;
	}

	/**
	 * 获取笔记本的笔记列表
	 * 
	 * @param request
	 * @param rowkey
	 * @return
	 */
	@RequestMapping("/getNoteListByNotebook")
	public ModelAndView getNoteListByNotebook(HttpServletRequest request,
			String rowkey) {
		ModelAndView modelAndView = null;
		try {
			// 获取笔记本的笔记列表
			List<Note> noteList = noteService.getNoteListByNotebook(rowkey);
			ModelMap map = new ModelMap();
			map.put("noteList", noteList);
			modelAndView = new ModelAndView(new MappingJacksonJsonView(), map);
		} catch (Exception e) {
			String userName = (String) request.getSession().getAttribute(
					Constants.USER_INFO);
			logger.error("用户" + userName
					+ "获取笔记本的笔记异常|方法getNoteListByNotebook|参数:rowkey:" + rowkey,
					e);
			e.printStackTrace();
		}
		return modelAndView;
	}

	/**
	 * 删除指定用户的笔记本 notename：笔记本名字
	 * 先判断当前笔记本下是否存在笔记,若存在则提示先删除笔记
	 * 完后 从Redis中删除指定的笔记本,若删除成功则继续删除HBASE的数据,删除HBASE数据失败则进行恢复Redis数据,成功则返回
	 * @param request
	 * @return
	 */
	@RequestMapping("/deleteNoteBook")
	public ModelAndView deleteNoteBook(HttpServletRequest request,
			String noteBookName, String rowKey) {
		ModelAndView modelAndView = null;
		// 分割rowKey，获取username和createTime
		String[] split = rowKey.split("\\" + Constants.ROWKEY_SEPARATOR);
		try {
			// 获取笔记本下的笔记信息
			List<Note> noteList = noteService.getNoteListByNotebook(rowKey);
			ModelMap map = new ModelMap();
			// 如果不为空，不允许删除笔记
			if (noteList != null && noteList.size() > 0) {
				map.put("success", false);
				map.put("message", "请先删除旗下笔记!");
			} else {
				// 删除指定用户的笔记本
				boolean flag = noteService.deleteNoteBook(noteBookName,
						split[0], split[1], 0);
				map.put("success", flag);
			}
			modelAndView = new ModelAndView(new MappingJacksonJsonView(), map);
		} catch (Exception e) {
			String userName = (String) request.getSession().getAttribute(
					Constants.USER_INFO);
			logger.error("用户" + userName
					+ "删除笔记本异常|方法:deleteNoteBook|参数： noteBookName:"
					+ noteBookName + ";rowKey:" + rowKey, e);
			e.printStackTrace();
		}
		return modelAndView;
	}

	/**
	 * 重命名笔记本 notename：笔记本名字
	 * 
	 * @param request
	 * @return
	 */
	@RequestMapping("/updateNoteBook")
	public ModelAndView updateNoteBook(HttpServletRequest request, String oldNoteBookName, String newNoteBookName, String rowKey) {
		ModelAndView modelAndView = null;
		try {
			// 分割row，取username和createTime
			String[] split = rowKey.split("\\" + Constants.ROWKEY_SEPARATOR);
			// 重命名笔记本
			boolean flag = noteService.updateNoteBook(newNoteBookName,
					oldNoteBookName, split[0], split[1], 0);
			ModelMap map = new ModelMap();
			map.put("success", flag);
			modelAndView = new ModelAndView(new MappingJacksonJsonView(), map);
		} catch (Exception e) {
			String userName = (String) request.getSession().getAttribute(
					Constants.USER_INFO);
			logger.error("用户" + userName
					+ "修改笔记本异常|方法:updateNoteBook|参数： oldNoteBookName:"
					+ oldNoteBookName + ";newNoteBookName:" + newNoteBookName
					+ ";rowKey:" + rowKey, e);
			e.printStackTrace();
		}
		return modelAndView;
	}

	/**
	 * 添加笔记
	 * 笔记的RowKey与笔记本一致
	 * @param request
	 * @param noteName
	 * @param noteBookRowkey
	 * @return
	 */
	@RequestMapping(value = "/addNote")
	public ModelAndView addNote(HttpServletRequest request, String noteName,
			String noteBookRowkey) {
		ModelAndView modelAndView = null;
		// 获取用户名
		String userName = (String) request.getSession().getAttribute(
				Constants.USER_INFO);
		try {
			// 创建时间
			Long createTimeLong = System.currentTimeMillis();
			// 添加笔记
			boolean addNote = noteService.addNote(userName
					+ Constants.ROWKEY_SEPARATOR + createTimeLong, noteName,
					createTimeLong.toString(), "0", noteBookRowkey);
			ModelMap map = new ModelMap();
			if (addNote) {
				map.put("resource", userName + Constants.ROWKEY_SEPARATOR
						+ createTimeLong);
				map.put("success", true);
			} else {
				map.put("success", false);
			}
			modelAndView = new ModelAndView(new MappingJacksonJsonView(), map);
		} catch (Exception e) {
			logger.error("用户" + userName + "添加笔记异常|方法:addNote|参数：noteName:"
					+ noteName + ";noteBookRowkey:" + noteBookRowkey, e);
			e.printStackTrace();
		}
		return modelAndView;
	}

	/**
	 * 获取笔记详情:根据rowkey 先找到对应的row，然后根据列族名 找到相应的列族,再根据相应的列名找到对应的列的值
	 * 
	 * @param request
	 * @param noteRowkey
	 * @return
	 */
	@RequestMapping("/getNote")
	public ModelAndView getNote(HttpServletRequest request, String noteRowkey) {
		ModelAndView modelAndView = null;
		try {
			// 查询笔记详情
			Note note = noteService.getNoteByRowKey(noteRowkey);
			ModelMap map = new ModelMap();
			if (note != null) {
				map.put("note", note);
				map.put("success", true);
			} else {
				map.put("success", false);
			}
			modelAndView = new ModelAndView(new MappingJacksonJsonView(), map);
		} catch (Exception e) {
			String userName = (String) request.getSession().getAttribute(
					Constants.USER_INFO);
			logger.error("用户" + userName + "获取笔记内容异常|方法：getNote|参数:noteRowkey:"
					+ noteRowkey, e);
			e.printStackTrace();
		}
		return modelAndView;
	}

	/**
	 * 删除笔记
	 * 
	 * @param request
	 * @param oldNoteName
	 * @param noteRowKey
	 * @param noteBookRowkey
	 * @return
	 */
	@RequestMapping(value = "/deleteNote")
	public ModelAndView deleteNote(HttpServletRequest request, String oldNoteName, String noteRowKey, String noteBookRowkey) {
		ModelAndView modelAndView = null;
		try {
			//分割rowkey和createtime
			String[] split = noteRowKey.split("\\" + Constants.ROWKEY_SEPARATOR);
			boolean delNote = noteService.deleteNote(noteRowKey, split[1], "0",
					oldNoteName, noteBookRowkey);
			ModelMap map = new ModelMap();
			map.put("success", delNote);
			modelAndView = new ModelAndView(new MappingJacksonJsonView(), map);
		} catch (Exception e) {
			String userName = (String) request.getSession().getAttribute(
					Constants.USER_INFO);
			logger.error("用户" + userName
					+ "删除笔记异常|方法:deleteNote|参数：oldNoteName:" + oldNoteName
					+ ";noteRowKey:" + noteRowKey + ";noteBookRowkey:"
					+ noteBookRowkey, e);
			e.printStackTrace();
		}
		return modelAndView;
	}

	/**
	 *
	 * @Description: 更新笔记
	 *
	 * @auther: 臧浩鹏
	 * @date: 17:22 2018/8/15
	 * @param: [request, noteName, oldNoteName, noteRowKey, content, noteBookRowkey]
	 * @return: org.springframework.web.servlet.ModelAndView
	 *
	 */

	@RequestMapping(value = "/updateNote")
	public ModelAndView updateNote(HttpServletRequest request, String noteName, String oldNoteName, String noteRowKey, String content, String noteBookRowkey) {
		ModelAndView modelAndView = null;
		ModelMap map= new ModelMap();
		//字数限制五十万
		if (content.length() > 500000) {
			map.put("success", false);
			modelAndView = new ModelAndView(new MappingJacksonJsonView(), map);
			return modelAndView;
		}
		try {
			//分割
			String[] split = noteRowKey
					.split("\\" + Constants.ROWKEY_SEPARATOR);
			boolean updateNote = noteService.updateNote(noteRowKey, noteName,
					split[1], content, "0", oldNoteName, noteBookRowkey);
			map.put("success", updateNote);
			modelAndView = new ModelAndView(new MappingJacksonJsonView(), map);
		} catch (Exception e) {
			String userName = (String) request.getSession().getAttribute(
					Constants.USER_INFO);
			logger.error("用户" + userName + "修改笔记异常|方法:updateNote|参数：noteName:"
					+ noteName + ";oldNoteName:" + oldNoteName + ";noteRowKey:"
					+ noteRowKey + ";content:" + content + ";noteBookRowkey:"
					+ noteBookRowkey, e);
			e.printStackTrace();
		}
		return modelAndView;
	}

	/**
	 * 移动并删除笔记,适用于删除到垃圾箱、笔记迁移
	 * 
	 * @param request
	 * @param noteName
	 * @param oldNoteBookRowkey
	 * @param noteRowKey
	 * 内容
	 * @param newNoteBookRowkey
	 * @return
	 */
	@RequestMapping(value = "/moveAndDeleteNote")
	public ModelAndView moveAndDeleteNote(HttpServletRequest request, String noteRowKey, String oldNoteBookRowkey, String newNoteBookRowkey,String noteName) {
		ModelAndView modelAndView = null;
		try {
			//移动笔记
			boolean moveNote = noteService.moveAndDeleteNote(noteRowKey,
					oldNoteBookRowkey, newNoteBookRowkey,noteName);
			ModelMap map = new ModelMap();
			map.put("success", moveNote);
			modelAndView = new ModelAndView(new MappingJacksonJsonView(), map);
		} catch (Exception e) {
			String userName = (String) request.getSession().getAttribute(
					Constants.USER_INFO);
			logger.error("用户" + userName
					+ "移动并删除笔记异常|方法：moveAndDeleteNote|参数：noteRowKey:"
					+ noteRowKey + ";oldNoteBookRowkey:" + oldNoteBookRowkey
					+ ";newNoteBookRowkey:" + newNoteBookRowkey, e);
			e.printStackTrace();
		}
		return modelAndView;
	}
	/**
	 * 显示活动列表页面
	 * 
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/showActivity")
	public String showActivity(HttpServletRequest request) throws Exception {
		return "active/activity";
	}

	/**
	 * 打开活动细节页面
	 * 
	 * @param request
	 * @param rowKey
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/openDetail")
	public String openDetail(HttpServletRequest request, String rowKey)
			throws Exception {
		request.setAttribute("rowKey", rowKey);
		return "active/activity_detail";
	}
	/**
	 * 活动笔记
	 * 
	 * @param request
	 * @param oldNoteBookRowkey
	 * @param noteRowKey
	 * @param newNoteBookRowkey
	 * @return
	 */
	@RequestMapping(value = "/activeMyNote")
	public ModelAndView activeMyNote(HttpServletRequest request,
			String noteRowKey, String oldNoteBookRowkey,
			String newNoteBookRowkey) {
		ModelAndView modelAndView = null;
		
		try {
			
			// String activityBtRowKey = userName+Constants.ACTIVITY;
			boolean moveNote = noteService.activeMyNote(noteRowKey,
					newNoteBookRowkey);
			ModelMap map = new ModelMap();
			map.put("success", moveNote);
			modelAndView = new ModelAndView(new MappingJacksonJsonView(), map);
		} catch (Exception e) {
			String userName = (String) request.getSession().getAttribute(
					Constants.USER_INFO);
			logger.error("用户" + userName
					+ "活动笔记异常|方法：activeMyNote|参数：noteRowKey:" + noteRowKey
					+ ";oldNoteBookRowkey:" + oldNoteBookRowkey
					+ ";newNoteBookRowkey:" + newNoteBookRowkey, e);
			e.printStackTrace();
		}
		return modelAndView;
	}

	
	/**
	 * 收藏笔记
	 * 
	 * @param request
	 * @param noteRowKey
	 * @return
	 */
	@RequestMapping(value = "/starOtherNote")
	public ModelAndView starOtherNote(HttpServletRequest request,
			String noteRowKey) {
		String userName = (String) request.getSession().getAttribute(
				Constants.USER_INFO);
		ModelAndView modelAndView = null;
		try {
			
			String starBtRowKey = userName + Constants.STAR;
			boolean moveNote = noteService.starOtherNote(noteRowKey,
					starBtRowKey);
			ModelMap map = new ModelMap();
			map.put("success", moveNote);
			modelAndView = new ModelAndView(new MappingJacksonJsonView(), map);
		} catch (Exception e) {
			logger.error("用户" + userName
					+ "收藏笔记异常|方法：starOtherNote|参数：noteRowKey:" + noteRowKey, e);
			e.printStackTrace();
		}
		return modelAndView;
	}

	
	
	/********************************************以下功能禁用***************************************************************/
	
	/**
	 * 分享笔记
	 * 
	 * @param rowKey
	 * @param
	 * @return
	 */
	@RequestMapping("/shareNote")
	public ModelAndView shareNote(HttpServletRequest request,
			HttpServletResponse response, String rowKey) {
		ModelMap map = new ModelMap();
		try {
			// 将笔记名字和内容创建索引，不存储，其他信息存储不索引
			boolean shareNote = noteService.shareNote(rowKey);
			if (shareNote) {
				map.put("success", true);
			} else {
				map.put("success", false);
			}
		} catch (Exception e) {
			logger.error("分享笔记异常:TechnologyController  &&  rowKey:" + rowKey, e);
			e.printStackTrace();
		}
		return new ModelAndView(new MappingJacksonJsonView(), map);
	}

	@RequestMapping("/search")
	public ModelAndView search(HttpServletRequest request,
			HttpServletResponse response, String key, Integer page) {
		ModelMap map = new ModelMap();
		List<Article> articles = new ArrayList<Article>();
		if (page == null || page.equals("")) {
			page = 1;
		}
		try {
			boolean tecFlag = false;
			articles = noteService.search(key, page);
			JSONArray wes = JSONArray.fromObject(articles);
			request.getSession().setAttribute("technologys", articles);
			request.getSession().setAttribute("tecFlag", tecFlag);
			map.put("wes", wes.toString());
			map.put("page", page);
			map.put("key", key);

		} catch (Exception e) {
			logger.error("从lucene中查询笔记异常:TechnologyController  &&  key:" + key,
					e);
			e.printStackTrace();

		}
		return new ModelAndView("question/questions_result", map);
	}

	/**
	 * 从lucene中分页查询更多笔记
	 * 
	 * @param key
	 *            :输入的关键字
	 * @param page
	 *            :页码
	 * @return
	 */
	@RequestMapping("/searchMore")
	public ModelAndView searchMore(HttpServletRequest request,
			HttpServletResponse response, String key, Integer page) {
		ModelMap map = new ModelMap();
		// 封装笔记信息
		List<Article> articles = new ArrayList<Article>();
		try {
			// 从lucene中查询笔记信息
			articles = noteService.search(key, page);
			// 将笔记信息list转为json
			JSONArray wes = JSONArray.fromObject(articles);
			map.put("urls", wes.toString());
			map.put("page", page + 1);
			map.put("key", key);
		} catch (Exception e) {
			logger.error("从lucene中查询更多笔记异常:TechnologyController  &&  key:"
					+ key + "；page" + page, e);
			e.printStackTrace();
		}
		return new ModelAndView(new MappingJacksonJsonView(), map);
	}
}
