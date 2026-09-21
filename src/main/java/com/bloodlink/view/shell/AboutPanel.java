package com.bloodlink.view.shell;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Who built BloodLink, what it is for, what it does with people's data.
 *
 * <h2>Why this is its own section</h2>
 * The gallery and the "about" material used to be a strip near the bottom of
 * the home page, which is the wrong place for both: the home page is where
 * someone goes to act on a request, and the gallery was competing with that
 * for attention while the privacy terms had nowhere to live at all. An app
 * that asks for a national ID number, a phone number, a home address, health
 * screening answers and a live location owes the person reading it a plain
 * statement of what it keeps and who sees it, somewhere they can find it
 * without hunting.
 *
 * <h2>Editing it</h2>
 * The team, gallery and contact details are read at runtime from
 * {@code resources/com/bloodlink/about/about.json}, so changing them is
 * editing that file. The privacy section is deliberately <em>not</em> in the
 * JSON: it describes what the code actually stores, so it belongs next to the
 * code that stores it and should change when that does.
 */
public final class AboutPanel extends VBox {

    private record Member(String name, String role, String photo, String note) { }
    private record Shot(String image, String caption) { }

    private final List<String> story = new ArrayList<>();
    private final List<Member> team = new ArrayList<>();
    private final List<Shot> gallery = new ArrayList<>();
    private String contactEmail = "";
    private String contactPhone = "";

    public AboutPanel() {
        setSpacing(30);
        load();
        getChildren().addAll(storyBlock(), teamBlock(), galleryBlock(), privacyBlock(), contactBlock());
    }

    private void load() {
        try (InputStream input = AboutPanel.class.getResourceAsStream("/com/bloodlink/about/about.json")) {
            if (input == null) return;
            JSONObject root = new JSONObject(new JSONTokener(input));

            JSONArray paragraphs = root.optJSONArray("story");
            for (int i = 0; paragraphs != null && i < paragraphs.length(); i++) {
                story.add(paragraphs.optString(i, ""));
            }

            JSONArray members = root.optJSONArray("team");
            for (int i = 0; members != null && i < members.length(); i++) {
                JSONObject member = members.getJSONObject(i);
                team.add(new Member(
                        member.optString("name", ""),
                        member.optString("role", ""),
                        member.optString("photo", ""),
                        member.optString("note", "")));
            }

            JSONArray shots = root.optJSONArray("gallery");
            for (int i = 0; shots != null && i < shots.length(); i++) {
                JSONObject shot = shots.getJSONObject(i);
                gallery.add(new Shot(shot.optString("image", ""), shot.optString("caption", "")));
            }

            JSONObject contact = root.optJSONObject("contact");
            if (contact != null) {
                contactEmail = contact.optString("email", "");
                contactPhone = contact.optString("phone", "");
            }
        } catch (Exception e) {
            // A broken file must not take the page down; the sections below
            // simply render with whatever was read before it failed.
            story.clear();
        }
    }

    private Node storyBlock() {
        VBox box = new VBox(12);
        box.getChildren().add(UI.sectionHead("About BloodLink", "Why this app exists."));
        VBox card = new VBox(12);
        card.getStyleClass().add("surface-card");
        if (story.isEmpty()) {
            card.getChildren().add(body("BloodLink connects verified blood donors to real emergencies nearby."));
        } else {
            story.forEach(paragraph -> card.getChildren().add(body(paragraph)));
        }
        box.getChildren().add(card);
        return box;
    }

    private Node teamBlock() {
        VBox box = new VBox(12);
        // No build instructions here. This page is shown to an audience, and
        // "drop a photo into images/team/" is addressed to someone who is not
        // in the room.
        box.getChildren().add(UI.sectionHead("The people behind it",
                "Built as a university project, by three of us."));

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        int columns = Math.max(1, Math.min(3, team.size()));
        for (int i = 0; i < columns; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / columns);
            grid.getColumnConstraints().add(column);
        }
        for (int i = 0; i < team.size(); i++) {
            grid.add(memberCard(team.get(i)), i % columns, i / columns);
        }
        box.getChildren().add(grid);
        return box;
    }

    private Node memberCard(Member member) {
        // An ImageSlot rather than a bare ImageView: it already handles the
        // not-yet-added case, and it keeps the same passport proportion the
        // rest of the app uses for faces. Silent about the file name, because
        // this card is the page's audience-facing content.
        Node portrait = new ImageSlot("team/" + member.photo(), "Photo", 116, 140, false);

        Label name = new Label(member.name());
        name.getStyleClass().add("about-member-name");
        name.setWrapText(true);

        Label role = new Label(member.role());
        role.getStyleClass().add("about-member-role");
        role.setWrapText(true);

        VBox card = new VBox(10, portrait, new VBox(1, name, role));
        card.setAlignment(Pos.TOP_LEFT);
        card.getStyleClass().add("surface-card");
        card.setMaxWidth(Double.MAX_VALUE);

        if (member.note() != null && !member.note().isBlank()) {
            Label note = new Label(member.note());
            note.getStyleClass().add("about-member-note");
            note.setWrapText(true);
            card.getChildren().add(note);
        }
        return card;
    }

    private Node galleryBlock() {
        VBox box = new VBox(12);
        box.getChildren().add(UI.sectionHead("Gallery", "Moments from drives and donations."));

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        int columns = Math.max(1, Math.min(3, gallery.size()));
        for (int i = 0; i < columns; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / columns);
            grid.getColumnConstraints().add(column);
        }
        // Fewer slots means each one gets more room rather than the row
        // keeping four-across proportions with empty space beside it.
        double slotWidth = columns <= 2 ? 440 : 290;
        for (int i = 0; i < gallery.size(); i++) {
            Shot shot = gallery.get(i);
            VBox cell = new VBox(8,
                    new ImageSlot(shot.image(), shot.caption(), slotWidth, 250, false),
                    caption(shot.caption()));
            grid.add(cell, i % columns, i / columns);
        }
        box.getChildren().add(grid);
        return box;
    }

    /**
     * What the app stores, stated in terms of the rows it actually writes.
     * Written from the schema rather than from a template, because a privacy
     * notice that does not match the database is worse than none.
     */
    private Node privacyBlock() {
        VBox box = new VBox(12);
        box.getChildren().add(UI.sectionHead("Privacy policy", "What BloodLink keeps, and who can see it."));

        Accordion accordion = new Accordion();
        accordion.getPanes().addAll(
                pane("What we store about you",
                        "Your name, email address, phone number, district and address; your blood group; and, "
                        + "if you added them, a profile photo, a national ID number and a guardian's name and "
                        + "phone number. Donors also have health screening answers -- weight, height, last "
                        + "donation date, chronic conditions, recent surgery, tattoos, medication, illness and "
                        + "pregnancy -- because those decide whether donating is safe for you."),
                pane("Your location",
                        "A location is stored only once you set one, either by allowing a lookup or by dragging "
                        + "the pin yourself. It is kept as a single latitude and longitude, not a history: "
                        + "setting a new one replaces the old. It is used to rank you against nearby requests "
                        + "and to compute the distances shown. You can clear it at any time from your profile, "
                        + "and matching then falls back to your district."),
                pane("Who can see what",
                        "Requesters see your name, blood group, district and approximate distance when you are "
                        + "matched to a request. Your phone number and email are shared only after you accept a "
                        + "request -- accepting is what opens contact, and declining shares nothing. "
                        + "Administrators can see your registration details and any documents you upload, in "
                        + "order to verify your account. Other donors see nothing about you except a leaderboard "
                        + "position, which you can make anonymous without losing your rank."),
                pane("What we never do",
                        "BloodLink does not sell or rent your details, does not pay for blood or charge for it, "
                        + "and does not share your data with advertisers. Partner vouchers are redeemed by "
                        + "issuing you a code; the partner is not given your identity."),
                pane("Deleting your data",
                        "You can export everything held about you, and request deletion, from the Privacy "
                        + "section of your profile. Deletion removes your account and personal details. "
                        + "Verified donation records are kept in anonymised form, because a requester's own "
                        + "history of a fulfilled request would otherwise be destroyed along with it."));
        box.getChildren().add(accordion);
        return box;
    }

    private TitledPane pane(String title, String text) {
        Label content = body(text);
        content.setPadding(new javafx.geometry.Insets(4, 4, 8, 4));
        TitledPane pane = new TitledPane(title, content);
        pane.setAnimated(true);
        return pane;
    }

    private Node contactBlock() {
        VBox box = new VBox(12);
        box.getChildren().add(UI.sectionHead("Contact", "For anything this page does not answer."));
        VBox card = new VBox(6);
        card.getStyleClass().add("surface-card");
        if (!contactEmail.isBlank()) card.getChildren().add(body("Email: " + contactEmail));
        if (!contactPhone.isBlank()) card.getChildren().add(body("Phone: " + contactPhone));
        if (card.getChildren().isEmpty()) {
            card.getChildren().add(body("Add contact details in resources/com/bloodlink/about/about.json."));
        }
        box.getChildren().add(card);
        return box;
    }

    private Label body(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("about-body");
        label.setWrapText(true);
        label.setMaxWidth(Region.USE_COMPUTED_SIZE);
        return label;
    }

    private Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("about-caption");
        label.setWrapText(true);
        return label;
    }
}
