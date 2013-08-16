package com.imaginarymachines.confluence.plugins;

import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.confluence.core.ListBuilder;
import com.atlassian.confluence.labels.LabelManager;
import com.atlassian.confluence.pages.*;
import com.atlassian.confluence.pages.actions.PageAware;
import com.atlassian.confluence.security.SpacePermission;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.spaces.SpacesQuery;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.plugin.webresource.UrlMode;
import com.atlassian.plugin.webresource.WebResourceUrlProvider;
import com.atlassian.spring.container.ContainerManager;
import com.atlassian.user.User;
import com.opensymphony.webwork.ServletActionContext;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
* This Confluence action allows to copy a page including
*/
public class TreecopyAction extends ConfluenceActionSupport implements PageAware {

    private static final String TOGGLE_PREFIX = "toggle-";

	private static final long serialVersionUID = 7584904352458114888L;

    private static final Logger LOG = Logger.getLogger(TreecopyAction.class);
    public static final String TARGETSPACE = "targetspace";
    public static final String PARENTTITLE = "parenttitle";

    private SpaceManager spaceManager;
	private PageManager pageManager;
	private AttachmentManager attachmentManager;
	private LabelManager labelManager;
	private AbstractPage page;
	private Page currPage;
	private List<CopyPage> descendants;
	private List<Space> spaces;
	private String staticResourcePrefix;

	public String executeSetnames() {

		currPage = (Page) this.getWebInterfaceContext().getPage();

		CopyPage currCopy = createCopyPage(currPage,0);

		descendants = new ArrayList<CopyPage>();
		currCopy.readChildHierarchy(descendants);

		spaces = getSpacesEditableByUser(AuthenticatedUserThreadLocal.getUser());

		WebResourceUrlProvider webResourceUrlProvider = (WebResourceUrlProvider)ContainerManager.getComponent("webResourceUrlProvider");
		staticResourcePrefix = webResourceUrlProvider.getStaticResourcePrefix(UrlMode.ABSOLUTE);

		return "setnames";
	}

	public String executeCopy() {

		currPage = (Page) this.getWebInterfaceContext().getPage();
		CopyPage currCopy = createCopyPage(currPage, 1);

		HttpServletRequest request = ServletActionContext.getRequest();

		@SuppressWarnings("unchecked")
		Enumeration<String> enumParams = request.getParameterNames();
		while (enumParams.hasMoreElements()) {
			String name = enumParams.nextElement();
			String value = request.getParameter(name);

			if (name.startsWith(TOGGLE_PREFIX) && name.endsWith(value)) {

				long originalPageId = Long.parseLong(value);
				CopyPage copypage = currCopy.getCopyPageById(originalPageId);
				if (copypage != null) {
					// Sprawdzamy czy zalaczniki istnieja na filesystemie.
					if (!areAttachmentsOk(pageManager.getPage(originalPageId))) {
						String pageTitle = pageManager.getPage(originalPageId)
								.getDisplayTitle();
						this.addActionError(
								"copypagetree.error.missing.attachment",
								new Object[] { pageTitle });
						return executeSetnames();
					}
					copypage.setToggle(true);
					copypage.setNewtitle(request.getParameter("title-" + value));
					if (LOG.isDebugEnabled()) {
						LOG.debug("COPY id=" + value + " newtitle="
								+ copypage.getNewtitle());
					}
				} else {
					if (LOG.isDebugEnabled()) {
						LOG.debug("CANT FIND id=" + value);
					}
				}

			}
		}

		Space space = spaceManager
				.getSpace(request.getParameter(TARGETSPACE));
		Page parentpage = pageManager.getPage(
				request.getParameter(TARGETSPACE),
				request.getParameter(PARENTTITLE));
		List<Space> allspaces = getSpacesEditableByUser(AuthenticatedUserThreadLocal
				.getUser());

		// Sprawdzamy czy strona o danej nazwie juz istnieje w przestrzeni.
		if (pageAlreadyExist(space, currCopy)) {
			this.addActionError("copypagetree.error.page.duplicate",
					new Object[] { currCopy.getNewtitle() });
			return executeSetnames();
		}

		if (space != null && parentpage == null) {
			parentpage = space.getHomePage();
		}

		if (parentpage == null) {
			this.addActionError("copypagetree.error.null.parentpage",
					new Object[] { space.getName() });
			return executeSetnames();
		}

		if (allspaces.contains(space)) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("SAVE under \"" + parentpage.getTitle() + "\" in \""
						+ space.getDisplayTitle() + "\"");
			}
			currCopy.storeCopyPages(space, pageManager, attachmentManager,
					labelManager, parentpage);
		} else {
			if (LOG.isDebugEnabled()) {
				LOG.debug("NOT SAVED under \"" + parentpage.getTitle()
						+ "\" due to insufficient permissions in Space "
						+ space.getKey());
			}
		}
		return "success";
	}


	private CopyPage createCopyPage(Page originalPage, int depth) {
		int position = 0;
		if (originalPage.getPosition()!=null) {
			position = originalPage.getPosition();
		}

		CopyPage pageCopy = new CopyPage(originalPage.getId(), position, depth, originalPage.getTitle());

		if (originalPage.getChildren().size()>0) {
			for (int i=0; i<originalPage.getChildren().size(); i++) {
				CopyPage childCopy = createCopyPage((Page)originalPage.getChildren().get(i), depth+1);
				pageCopy.addChild(childCopy);
			}
		}
		return pageCopy;
	}

	public List<Space> getSpaces() {
		return spaces;
	}

	public Page getCurrPage() {
		return currPage;
	}

	public List<CopyPage> getDescendants() {
		return descendants;
	}

	/**
	* Implementation of PageAware
	*/
	public AbstractPage getPage() {
		return page;
	}

	/**
	* Implementation of PageAware
	*/
	public void setPage(AbstractPage page) {
		this.page = page;
	}

	/**
	* Implementation of PageAware:
	* Returning 'true' ensures that the
	* page is set before the action commences.
	*/
	public boolean isPageRequired() {
		return true;
	}

	/**
	* Implementation of PageAware:
	* Returning 'true' ensures that the
	* current version of the page is used.
	*/
	public boolean isLatestVersionRequired() {
		return true;
	}

	/**
	* Implementation of PageAware:
	* Returning 'true' ensures that the user
	* requires page view permissions.
	*/
	public boolean isViewPermissionRequired() {
		return true;
	}

	/**
	* Dependency-injection of the Confluence LabelManager.
	*/
	public void setLabelManager(LabelManager labelManager) {
		this.labelManager = labelManager;
	}

	public void setPageManager(PageManager pageManager) {
		this.pageManager = pageManager;
	}

    public void setSpaceManager(SpaceManager spaceManager) {
        this.spaceManager = spaceManager;
    }

    public void setAttachmentManager(AttachmentManager attachmentManager) {
        this.attachmentManager = attachmentManager;
    }

    public String getStaticResourcePrefix() {
		if (LOG.isDebugEnabled()) {
			LOG.debug("staticResourcePrefix: "+this.staticResourcePrefix);
		}
    	return this.staticResourcePrefix;
    }

    private List<Space> getSpacesEditableByUser(User user){
		// Szukamy przestrzeni w ktorych user moze tworzyc/edytowac strony.
		// Beda to potencjalne przestrzenie do ktorych user moze skopiowac page tree.
		SpacesQuery spacesQuery = SpacesQuery.newQuery()
				.forUser(user)
				.withPermission(SpacePermission.CREATEEDIT_PAGE_PERMISSION)
				.build();
		ListBuilder<Space> spacesBuilder = spaceManager.getSpaces(spacesQuery);

		// Wyciagamy cala liste naraz.
		// Teoretycznie nie powinnismy tego robic, ale chodzi o obiekty Space, ktorych jest stosunkowo malo.
		return spacesBuilder.getRange(0, spacesBuilder.getAvailableSize() - 1);

    }

	private boolean areAttachmentsOk(Page pageToCheck) {
		String attachmentDir = AttachmentUtils.getConfluenceAttachmentDirectory();
		List<Attachment> attachments = pageToCheck.getLatestVersionsOfAttachments();

		if (attachments != null) {
			for (Attachment attachment : attachments) {

				// Tworzymy sciezke do zalacznika wg atlassianowego algorytmu.
				long attachmentId = attachment.getId();
				long attachmentContentId = attachment.getContent().getId();
				long attachmentContentIdLsd1 = attachmentContentId % 250;
				long attachmentContentIdLsd2 = ((attachmentContentId - (attachmentContentId %1000)) / 1000) % 250;
				String spaceSubdirs  = "";
			    if (attachment.getSpace() != null ) {
					long spaceId = attachment.getSpace().getId();
					long spaceIdLsd1 = spaceId % 250;
					long spaceIdLsd2 = ((spaceId - (spaceId %1000)) / 1000) % 250;
					spaceSubdirs = spaceIdLsd1 + "/" + spaceIdLsd2 + "/" + spaceId;
			    }
			    else {
			    	spaceSubdirs = "nonspaced";
			    }
				File expectedName = new File(attachmentDir +
											 "/ver003/" +
											 spaceSubdirs +
											 "/" +
											 attachmentContentIdLsd1 +
											 "/" +
											 attachmentContentIdLsd2 +
											 "/" +
											 attachmentContentId +
											 "/" +
											 attachmentId +
											 "/" +
											 attachment.getVersion());


				if (!expectedName.exists())
				{
					if (LOG.isDebugEnabled()) {
						LOG.debug("Attachment " + expectedName.getAbsolutePath() + " does not exist.");
					}
					return false;
				}
			}
		}
		return true;
	}

	private boolean pageAlreadyExist(Space space, CopyPage currCopy) {

		Page page = pageManager.getPage(space.getKey(), currCopy.getNewtitle());
		if (page == null) {
			return false;
		}
		return true;
	}

}