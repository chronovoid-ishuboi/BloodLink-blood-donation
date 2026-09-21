package com.bloodlink.view.components;

import com.bloodlink.model.BloodGroup;
import javafx.scene.control.Accordion;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The Help tab shared by both dashboards: the animated compatibility chart for
 * the viewer's own blood group, then the FAQ.
 * <p>
 * The chart is rebuilt rather than reused when the group changes
 * ({@link #setGroup}) so its draw-in animation replays for the new group --
 * a chart that silently swapped its highlights would be easy to miss.
 */
public final class HelpFaqView extends VBox {

    private final BloodCompatibilityView.Mode mode;
    private BloodCompatibilityView chart;

    public HelpFaqView(BloodGroup group, BloodCompatibilityView.Mode mode) {
        this.mode = mode;
        setSpacing(22);
        getChildren().addAll(chartHolder(group), faqSection(), eligibilitySection());
    }

    private VBox chartHolder(BloodGroup group) {
        VBox holder = new VBox();
        holder.getStyleClass().add("content-card");
        holder.setId("compatibility-holder");
        if (group != null) {
            chart = new BloodCompatibilityView(group, mode);
            holder.getChildren().add(chart);
        } else {
            holder.getChildren().add(placeholder());
        }
        return holder;
    }

    /** Swaps in a chart for a different group, replaying the animation. Pass null to show the placeholder. */
    public void setGroup(BloodGroup group) {
        VBox holder = (VBox) getChildren().get(0);
        if (chart != null) chart.stop();
        if (group == null) {
            chart = null;
            holder.getChildren().setAll(placeholder());
            return;
        }
        chart = new BloodCompatibilityView(group, mode);
        holder.getChildren().setAll(chart);
    }

    private Label placeholder() {
        Label label = new Label(mode == BloodCompatibilityView.Mode.DONOR
                ? "Your blood group will appear here once your donor profile is complete."
                : "Select one of your requests to see which donors can give that blood group.");
        label.getStyleClass().add("helper-text");
        label.setWrapText(true);
        return label;
    }

    private VBox faqSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("content-card");
        Label title = new Label("Frequently asked questions");
        title.getStyleClass().add("section-title");

        Accordion accordion = new Accordion();
        for (String[] entry : FAQ) {
            Label answer = new Label(entry[1]);
            answer.setWrapText(true);
            answer.getStyleClass().add("helper-text");
            VBox body = new VBox(answer);
            body.setStyle("-fx-padding: 6 2 10 2;");
            accordion.getPanes().add(new TitledPane(entry[0], body));
        }
        VBox.setVgrow(accordion, Priority.ALWAYS);
        section.getChildren().addAll(title, accordion);
        return section;
    }

    private VBox eligibilitySection() {
        VBox section = new VBox(8);
        section.getStyleClass().add("content-card");
        Label title = new Label("Who can donate blood");
        title.getStyleClass().add("section-title");
        section.getChildren().add(title);
        for (String rule : ELIGIBILITY) {
            Label line = new Label("•  " + rule);
            line.setWrapText(true);
            line.getStyleClass().add("helper-text");
            section.getChildren().add(line);
        }
        return section;
    }

    private static final List<String[]> FAQ = List.of(
            new String[]{"How does BloodLink match me with a donor?",
                    "When you submit a request, BloodLink ranks eligible donors by blood-group compatibility, distance from the hospital you named, how recently they last donated, their availability status, and their reputation from past verified donations. Donors are notified in that order; you see the ranked list on your request."},
            new String[]{"How long do I have to wait between donations?",
                    "A donor enters a cooldown period after each verified donation, and BloodLink will not match them to a new request until that period is over. Your Overview tab shows exactly how much of your cooldown remains."},
            new String[]{"What does 'verified donation' mean?",
                    "A donation is only counted once both sides confirm it: the donor confirms they donated, and the requester confirms they received. Until both confirmations are in, nothing is added to donation history, donor statistics, or points."},
            new String[]{"How do points work?",
                    "Donors earn points for each verified donation, plus a bonus each time a requester saves them as a favorite donor. Points can be redeemed for partner vouchers from the Rewards tab. Points are only ever awarded for real, confirmed events."},
            new String[]{"What happens if a donor does not respond?",
                    "Requests can be re-matched at any time from your request list, which notifies additional eligible donors without removing anyone who has already accepted. A request needing several units keeps collecting donors until it is fully fulfilled."},
            new String[]{"Is my contact information visible to everyone?",
                    "No. A donor's phone number is only revealed to a requester once that donor has accepted the request. Before that, the profile shows their blood group, area, availability and reputation, but contact details stay locked."},
            new String[]{"Why was my NID scan rejected or not recognised?",
                    "NID scanning assists registration by reading a photo of your card, but the result is always shown to you for review before anything is saved, and it is never treated as verified proof. If the scan fails or misreads a field, you can simply type the details in manually."});

    private static final List<String> ELIGIBILITY = List.of(
            "You are generally between 18 and 60 years old.",
            "You weigh at least 50 kg.",
            "It has been at least 3 months since your last whole-blood donation.",
            "You are not currently ill, feverish, or recovering from an infection.",
            "You have not had surgery, a tattoo, or a piercing in the last 6 months.",
            "You are not pregnant, and have not given birth in the last 6 months.",
            "Final eligibility is always decided by medical staff at the donation centre -- this list is guidance, not a medical clearance.");
}
