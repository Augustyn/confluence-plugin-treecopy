package com.imaginarymachines.confluence.plugins;

import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.spring.container.ContainerManager;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.user.User;
import com.imaginarymachines.com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;


public class PagesServlet extends HttpServlet {

    private static final long serialVersionUID = -1227774810957633829L;
    public static final String ACTION = "action";
    public static final String ACTION_GETPAGES = "getpages";

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		String actionName = request.getParameter(ACTION);

		if (actionName == null || !actionName.equals(ACTION_GETPAGES)) {
            return;
        }

        	PageManager pageManager = (PageManager) ContainerManager.getComponent("pageManager");
            SpaceManager spaceManager = (SpaceManager) ContainerManager.getComponent("spaceManager");
            PermissionManager permissionManager = (PermissionManager) ContainerManager.getComponent("permissionManager");

            User user = AuthenticatedUserThreadLocal.getUser();
            String spacekey = request.getParameter("spacekey");
        	String term = request.getParameter("value");

            Space space = spaceManager.getSpace(spacekey);

            List<Map<String, String>> pagemap = new ArrayList<Map<String, String>>();

            if ( term != null && !term.isEmpty() && space != null ) {
            	@SuppressWarnings("unchecked")
				List<Page> pages = pageManager.getPagesStartingWith(space, term);
                for (Page page : pages) {
                	if(permissionManager.hasPermission(user, Permission.VIEW, page)) {
                		Map<String, String> entry = new HashMap<String, String>();
                		entry.put("value", page.getTitle());
                		pagemap.add(entry);
                	}
    			}
			}
            Gson gson = new Gson();
            response.getWriter().write(gson.toJson(pagemap));
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}
}