package com.imaginarymachines.confluence.plugins;

import com.atlassian.confluence.event.events.content.page.PageMoveEvent;
import com.atlassian.confluence.labels.Label;
import com.atlassian.confluence.labels.LabelManager;
import com.atlassian.confluence.labels.Labelable;
import com.atlassian.confluence.pages.*;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.spring.container.ContainerManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CopyPage implements Comparable<CopyPage> {

    private static final Logger LOG = Logger.getLogger(CopyPage.class);

	private int position;
	private String title;
	private String newtitle;
	private boolean toggle;
	private int depth;
	private long id;
	private long newid;
	private CopyPage parent;

	private List<CopyPage> children = new ArrayList<CopyPage>();

	public CopyPage(long id, int position, int depth, String title) {
		this.id=id;
		this.position = position;
		this.depth = depth;
		this.title = title;
	}

	public void addChild(CopyPage childcopy) {
		childcopy.setParent(this);
		this.children.add(childcopy);
	}

	public void readChildHierarchy(List<CopyPage> level) {
		level.add(this);
		List<CopyPage> childs = this.getChildren();
		for (int i=0; i<childs.size(); i++) {
			CopyPage child = (CopyPage)childs.get(i);
			child.readChildHierarchy(level);
		}
	}

	private List<CopyPage> getChildren() {
		Collections.sort(this.children);
		return this.children;
	}

	public CopyPage getCopyPageById(long id) {

		if (this.id==id) {
			return this;
		} else {
			List<CopyPage> childs = this.getChildren();
			for (int i=0; i<childs.size(); i++) {
				CopyPage child = (CopyPage)childs.get(i);
				CopyPage match = child.getCopyPageById(id);
				if (match!=null) return match;
			}
		}

		return null;
	}

	public void storeCopyPages(Space space, PageManager pageManager, AttachmentManager attachmentManager, LabelManager labelManager, Page defaultParentPage) {

		if (this.toggle) this.storeCopyPage(space, pageManager, attachmentManager, labelManager, defaultParentPage);

		List<CopyPage> childs = this.getChildren();
		for (int i=0; i<childs.size(); i++) {
			CopyPage child = (CopyPage)childs.get(i);
			try {
				child.storeCopyPages(space, pageManager, attachmentManager, labelManager, defaultParentPage);
			} catch (DuplicateDataRuntimeException e) {
				LOG.warn("Page " + child.getId() + " named: " + child.getTitle() + " already exists in space: " + space.getKey() );
			}
		}
	}

	private void storeCopyPage(Space space, PageManager pageManager, AttachmentManager attachmentManager, LabelManager labelManager, Page defaultParentPage) {

		Page oldPage = pageManager.getPage(this.id);

//		Page parentPage = defaultParentPage;
//		if (this.getParent()!=null) {
//			parentPage = pageManager.getPage(this.getParent().getNewid());
//		}
		long parentPageId = findFirstAvailableParent(pageManager, defaultParentPage);
		Page parentPage = pageManager.getPage(parentPageId);


		final Page newPage = new Page();
        newPage.setTitle(this.getNewtitle());
        newPage.setSpace(space);
        newPage.setBodyAsString(oldPage.getBodyAsString());
        newPage.setPosition(oldPage.getPosition());
        pageManager.saveContentEntity(newPage, null);

        // JICO-1878 - confluence ma wbudowany mechanizm który ogarnia
        // wszystkie referencje do i od strony która zmienia space - wystarczy odpalić event PageMoveEvent
        // patrz: com.atlassian.confluence.links.RelatedContentRefactoringListener
        EventPublisher eventPublisher = (EventPublisher) ContainerManager.getComponent("eventPublisher");
        eventPublisher.publish(new PageMoveEvent(this, newPage, oldPage.getSpace(), oldPage.getParent(), oldPage.getPosition(), AuthenticatedUserThreadLocal.getUser()));

        if (parentPage!=null) {
        	parentPage.addChild(newPage);
        } else {
        	if (LOG.isDebugEnabled()) {
				LOG.debug("No parent page.");
			}
        }
        this.setNewid(newPage.getId());

        List<Attachment> oldAttachments = oldPage.getLatestVersionsOfAttachments();
        for (Attachment oldAttachment : oldAttachments) {
        	try {
            	if (LOG.isDebugEnabled()) {
    				LOG.debug("oldAttachment="+oldAttachment.getFileName());
    			}
        		Attachment newAttachment = new Attachment();
        		newAttachment.setContentType(oldAttachment.getContentType());
        		newAttachment.setFileName(oldAttachment.getFileName());
        		newAttachment.setComment(oldAttachment.getComment());
        		newAttachment.setFileSize(oldAttachment.getFileSize());
	            newPage.addAttachment(newAttachment);
        		attachmentManager.saveAttachment(newAttachment, null, oldAttachment.getContentsAsStream());
            	if (LOG.isDebugEnabled()) {
    				LOG.debug("newAttachment="+newAttachment.getFileName());
    			}
            } catch (Exception exception) {
            	exception.printStackTrace();
            }

        }

        List<Label> oldLabels = oldPage.getLabels();
        for (Label oldLabel : oldLabels) {
        	labelManager.addLabel((Labelable)newPage, oldLabel);
        }
	}

	/**
	 * Rekurencja.
	 * Te metoda stara się znaleźć najbliższego parenta aktualnie kopiowanej strony (this),
	 * który także będzie kopiowany. Gdy taki się nie znajdzie, jako parenta przyjmujemy stronę podaną przez usera jako "Parent Page"/
	 * @return id strony parenta
	 */
	private long findFirstAvailableParent(PageManager pageManager, Page defaultParentPage) {

		if ( this.getParent() == null ){
			// jesteśmy na samej górze - tak więc naszym parentem będzie ten który ustawił user
			return defaultParentPage.getId();
		}

		long parentPageId = this.getParent().getNewid();

		if (parentPageId == 0){
			// parent nie będzie kopiowany - user go odznaczył - szukamy dalej
			parentPageId = this.getParent().findFirstAvailableParent(pageManager, defaultParentPage);
		}
		return parentPageId;
	}

	public int getPosition() {
		return position;
	}

	public String getTitle() {
		return title;
	}

	public int getDepth() {
		return depth;
	}

	public long getId() {
		return id;
	}

	public boolean isToggle() {
		return toggle;
	}

	public void setToggle(boolean toggle) {
		this.toggle = toggle;
	}

	public String getNewtitle() {
		return newtitle;
	}

	public void setNewtitle(String newtitle) {
		this.newtitle = newtitle;
	}


	public long getNewid() {
		return newid;
	}

	public void setNewid(long newid) {
		this.newid = newid;
	}

	public CopyPage getParent() {
		return parent;
	}

	public void setParent(CopyPage parent) {
		this.parent = parent;
	}

	public int compareTo(CopyPage copyPage) {
		//a negative integer, zero, or a positive integer as this object is less than, equal to,
		// or greater than the specified object.
		if (this.position<copyPage.position) return -1;
		if (this.position==copyPage.position) return 0;
		if (this.position>copyPage.position) return 1;
		return 0;
	}



}
