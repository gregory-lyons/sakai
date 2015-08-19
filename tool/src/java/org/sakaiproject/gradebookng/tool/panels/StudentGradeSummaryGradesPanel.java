package org.sakaiproject.gradebookng.tool.panels;

import org.apache.commons.lang.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.sakaiproject.gradebookng.business.GradebookNgBusinessService;
import org.sakaiproject.gradebookng.business.model.GbGradeInfo;
import org.sakaiproject.gradebookng.business.model.GbStudentGradeInfo;
import org.sakaiproject.gradebookng.business.util.FormatHelper;
import org.sakaiproject.gradebookng.tool.pages.GradebookPage;
import org.sakaiproject.service.gradebook.shared.Assignment;
import org.sakaiproject.service.gradebook.shared.CategoryDefinition;
import org.sakaiproject.tool.gradebook.Gradebook;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class StudentGradeSummaryGradesPanel extends Panel {

	private static final long serialVersionUID = 1L;
	
	private GbStudentGradeInfo gradeInfo;
	private List<CategoryDefinition> categories;
	
	@SpringBean(name="org.sakaiproject.gradebookng.business.GradebookNgBusinessService")
	protected GradebookNgBusinessService businessService;

	public StudentGradeSummaryGradesPanel(String id, IModel<Map<String, Object>> model) {
		super(id, model);
	}

		@Override
	public void onInitialize() {
		super.onInitialize();
		
		//unpack model
		Map<String,Object> modelData = (Map<String,Object>) this.getDefaultModelObject();
		String userId = (String) modelData.get("userId");
		String displayName = (String) modelData.get("displayName");
		
		//build the grade matrix for the user
		final List<Assignment> assignments = this.businessService.getGradebookAssignments();
        
		//TODO catch if this is null, the get(0) will throw an exception
		//TODO also catch the GbException
		this.gradeInfo = this.businessService.buildGradeMatrix(assignments, Collections.singletonList(userId)).get(0);
		this.categories = this.businessService.getGradebookCategories();

		final List<String> categoryNames = new ArrayList<String>();
		final Map<String, List<Assignment>> categoriesToAssignments = new HashMap<String, List<Assignment>>();

		Iterator<Assignment> assignmentIterator = assignments.iterator();
		while (assignmentIterator.hasNext()) {
			Assignment assignment = assignmentIterator.next();
			String category = assignment.getCategoryName() == null ? GradebookPage.UNCATEGORIZED : assignment.getCategoryName();

			if (!categoriesToAssignments.containsKey(category)) {
				categoryNames.add(category);
				categoriesToAssignments.put(category, new ArrayList<Assignment>());
			}

			categoriesToAssignments.get(category).add(assignment);
		}

		Collections.sort(categoryNames);

		final boolean[] categoryScoreHidden = { false };

		add(new ListView<String>("categoriesList", categoryNames) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void populateItem(ListItem<String> categoryItem) {
				final String category = categoryItem.getModelObject();

				List<Assignment> categoryAssignments = categoriesToAssignments.get(category);

				categoryItem.add(new Label("category", category));

				CategoryDefinition categoryDefinition = null;
				for (CategoryDefinition aCategoryDefinition : categories) {
					if (aCategoryDefinition.getName().equals(category)) {
						categoryDefinition = aCategoryDefinition;
						break;
					}
				}

				if (categoryDefinition != null) {
					boolean allAssignmentsAreReleased = true;
					for (Assignment assignment : categoryAssignments) {
						if (!assignment.isReleased()) {
							allAssignmentsAreReleased = false;
							break;
						}
					}

					String weight = "";
					if (categoryDefinition.getWeight() == null) {
						categoryItem.add(new Label("categoryWeight", ""));
					} else {
						weight = FormatHelper.formatDoubleAsPercentage(categoryDefinition.getWeight() * 100);
						categoryItem.add(new Label("categoryWeight", new StringResourceModel("label.studentsummary.categoryweight", null, new Object[] {weight})));
					}

					if (allAssignmentsAreReleased) {
						Double score = gradeInfo.getCategoryAverages().get(categoryDefinition.getId());
						String grade = "";
						if (score != null) {
							grade = FormatHelper.formatDoubleAsPercentage(score);
						}
						categoryItem.add(new Label("categoryGrade", grade));
					} else {
						categoryScoreHidden[0] = true;
						categoryItem.add(new Label("categoryGrade", getString("label.studentsummary.categoryscoreifhiddenassignment")));
					}
				} else {
					categoryItem.add(new Label("categoryGrade", ""));
					categoryItem.add(new Label("categoryWeight", ""));
				}

				categoryItem.add(new ListView<Assignment>("assignmentsForCategory", categoryAssignments) {
					private static final long serialVersionUID = 1L;

					@Override
					protected void populateItem(ListItem<Assignment> assignmentItem) {
						final Assignment assignment = assignmentItem.getModelObject();

						if (!assignment.isReleased()) {
							assignmentItem.setVisible(false);
						}

						GbGradeInfo gradeInfo = StudentGradeSummaryGradesPanel.this.gradeInfo.getGrades().get(assignment.getId());

						final String rawGrade;
						String comment;
						if(gradeInfo != null) {
							rawGrade = gradeInfo.getGrade();
							comment = gradeInfo.getGradeComment();
						} else {
							rawGrade = "";
							comment = "";
						}

						Label title = new Label("title", assignment.getName());
						assignmentItem.add(title);

						assignmentItem.add(new Label("dueDate", FormatHelper.formatDate(assignment.getDueDate(), getString("label.studentsummary.noduedate"))));
						assignmentItem.add(new Label("grade", FormatHelper.formatGrade(rawGrade)));
						assignmentItem.add(new Label("outOf",  new StringResourceModel("label.studentsummary.outof", null, new Object[] { assignment.getPoints() })) {
							@Override
							public boolean isVisible() {
								return rawGrade != "";
							}
						});
						assignmentItem.add(new Label("comments", comment));
					}
				});
			}
		});

		final Gradebook gradebook = businessService.getGradebook();
		if (gradebook.isCourseGradeDisplayed()) {
			add(new Label("courseGrade", this.gradeInfo.getCourseGrade()));
		} else {
			add(new Label("courseGrade", getString("label.studentsummary.coursegradenotreleased")));
		}

		add(new AttributeModifier("data-studentid", userId));

		add(new Label("categoryScoreNotReleased", getString("label.studentsummary.categoryscorenotreleased")) {
			@Override
			public boolean isVisible() {
				return categoryScoreHidden[0];
			}
		});
	}
}
