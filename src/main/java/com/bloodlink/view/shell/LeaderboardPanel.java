package com.bloodlink.view.shell;

import com.bloodlink.model.LeaderboardEntry;
import com.bloodlink.service.LeaderboardService;
import com.bloodlink.util.Avatars;
import com.bloodlink.util.BackgroundTasks;
import com.bloodlink.util.Icons;
import com.bloodlink.util.PhotoCache;
import com.bloodlink.view.components.BadgeView;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import com.bloodlink.util.AlertUtil;
import com.bloodlink.view.components.DonorProfileDialog;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The donor leaderboard: a podium for the top three, then the ranked list.
 * <p>
 * Ranked on verified donations -- the only donation count this app trusts
 * anywhere -- with points breaking ties. A donor who opted out of being named
 * still holds their place and reads as "Anonymous donor"; the substitution
 * happens in the DAO so no path here can leak a name.
 */
public final class LeaderboardPanel extends VBox {

    private final LeaderboardService leaderboardService = new LeaderboardService();
    private final VBox podiumHost = new VBox();
    private final VBox listHost = new VBox(10);
    private final Label myRankLabel = new Label();

    public LeaderboardPanel() {
        setSpacing(22);

        myRankLabel.getStyleClass().add("section-head-sub");

        getChildren().addAll(podiumHost, new VBox(6,
                headed("Full ranking", "Everyone with at least one verified donation."), myRankLabel), listHost);
        refresh();
    }

    private Node headed(String title, String subtitle) {
        return UI.sectionHead(title, subtitle);
    }

    /** Reloads off the FX thread; safe to call after a donation is confirmed. */
    public void refresh() {
        BackgroundTasks.run(
                () -> {
                    List<LeaderboardEntry> top = leaderboardService.topDonors(50);
                    return new Loaded(top, leaderboardService.myEntry().orElse(null));
                },
                loaded -> render(loaded.entries(), loaded.mine()),
                error -> listHost.getChildren().setAll(note("Could not load the leaderboard.")));
    }

    private record Loaded(List<LeaderboardEntry> entries, LeaderboardEntry mine) { }

    private void render(List<LeaderboardEntry> entries, LeaderboardEntry mine) {
        podiumHost.getChildren().clear();
        listHost.getChildren().clear();

        if (entries.isEmpty()) {
            myRankLabel.setText("");
            listHost.getChildren().add(note("No verified donations have been recorded yet. The first one goes here."));
            return;
        }

        podiumHost.getChildren().add(podium(entries));

        boolean inTopSlice = entries.stream().anyMatch(LeaderboardEntry::isViewer);
        // Prefer the position actually shown on the board. The computed rank
        // counts only donors strictly above you, so everyone tied on the same
        // number of donations computes as #1 -- which would read "You are
        // ranked #1" while the list plainly shows you third.
        Integer shownRank = entries.stream()
                .filter(LeaderboardEntry::isViewer)
                .map(LeaderboardEntry::rank)
                .findFirst()
                .orElse(mine == null ? null : mine.rank());
        myRankLabel.setText(shownRank == null
                ? "You have no verified donations yet, so you are not ranked."
                : "You are ranked #" + shownRank + ".");

        for (LeaderboardEntry entry : entries) {
            listHost.getChildren().add(row(entry));
        }

        // Someone ranked far down still needs to see where they stand, so
        // their own row is pinned after the visible slice rather than the
        // board simply not containing them.
        if (!inTopSlice && mine != null) {
            Label divider = new Label("· · ·");
            divider.getStyleClass().add("rank-gap");
            divider.setMaxWidth(Double.MAX_VALUE);
            divider.setAlignment(Pos.CENTER);

            Label yourPosition = new Label("Your position");
            yourPosition.getStyleClass().add("section-head-title");

            listHost.getChildren().addAll(divider, yourPosition, row(mine));
        }
    }

    /** Top three, ordered 2-1-3 so the winner stands in the middle. */
    private Node podium(List<LeaderboardEntry> entries) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.BOTTOM_CENTER);
        int[] order = {1, 0, 2};
        for (int slot : order) {
            if (slot >= entries.size()) continue;
            row.getChildren().add(podiumCard(entries.get(slot)));
        }
        return row;
    }

    private Node podiumCard(LeaderboardEntry entry) {
        Label place = new Label("#" + entry.rank());
        place.getStyleClass().addAll("podium-place", "podium-place-" + entry.rank());

        Label name = new Label(entry.displayName());
        name.getStyleClass().add("podium-name");
        name.setWrapText(true);
        name.setMaxWidth(150);

        Label count = new Label(entry.verifiedDonations() + (entry.verifiedDonations() == 1 ? " donation" : " donations"));
        count.getStyleClass().add("podium-count");

        VBox card = new VBox(8, avatar(entry, 64), place, name, count, new BadgeView(entry.badgeTier()));
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("podium-card");
        if (entry.rank() == 1) card.getStyleClass().add("podium-card-first");
        if (entry.isViewer()) card.getStyleClass().add("podium-card-you");
        makeOpenable(card, entry);
        return card;
    }

    private Node row(LeaderboardEntry entry) {
        Label rank = new Label("#" + entry.rank());
        rank.getStyleClass().add("rank-number");
        rank.setMinWidth(46);

        Label name = new Label(entry.displayName() + (entry.isViewer() ? "  (you)" : ""));
        name.getStyleClass().add("match-card-name");

        HBox meta = new HBox(10);
        meta.setAlignment(Pos.CENTER_LEFT);
        meta.getChildren().add(iconText(Icons.DROPLET, entry.bloodGroup().getDisplayName()));
        if (entry.district() != null && !entry.district().isBlank()) {
            meta.getChildren().add(iconText(Icons.MAP_PIN, entry.district()));
        }

        VBox identity = new VBox(3, name, meta);
        HBox.setHgrow(identity, Priority.ALWAYS);

        Label donations = new Label(String.valueOf(entry.verifiedDonations()));
        donations.getStyleClass().add("rank-metric");
        Label donationsLabel = new Label("donations");
        donationsLabel.getStyleClass().add("metric-label");
        VBox donationBox = new VBox(0, donations, donationsLabel);
        donationBox.setAlignment(Pos.CENTER_RIGHT);

        HBox card = new HBox(14, rank, avatar(entry, 40), identity, new BadgeView(entry.badgeTier()), donationBox);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("rank-row");
        if (entry.isViewer()) card.getStyleClass().add("rank-row-you");
        makeOpenable(card, entry);
        return card;
    }

    /**
     * Clicking a row or a podium card opens that donor's profile.
     *
     * <h2>Except when they asked not to be named</h2>
     * A donor who opted out of the leaderboard still holds their rank, and the
     * board shows them as "Anonymous donor". Opening a profile from that row
     * would print the real name they just asked to withhold -- the opt-out
     * would protect the list and nothing else. So anonymous rows are not
     * clickable, and say why when clicked, rather than being silently inert.
     * <p>
     * Contact details are not a concern here: the profile dialog is given no
     * match status, so it locks the phone number and email for anyone who is
     * not an admin. Rank, district and donation count are already on the board.
     */
    private void makeOpenable(javafx.scene.Node card, LeaderboardEntry entry) {
        if (entry.anonymous()) {
            Tooltip.install(card, new Tooltip("This donor chose not to be named."));
            card.setOnMouseClicked(event -> AlertUtil.info("Anonymous donor",
                    "This donor has opted out of being named on the leaderboard, so their profile is not shown. "
                    + "Their rank is real and counted the same as everyone else's."));
            return;
        }
        card.getStyleClass().add("rank-row-clickable");
        Tooltip.install(card, new Tooltip("Open " + entry.displayName() + "'s profile"));
        card.setOnMouseClicked(event -> DonorProfileDialog.show(
                card.getScene() == null ? null : card.getScene().getWindow(),
                entry.donorId(), null, null));
    }

    /** An anonymous donor gets the neutral glyph, never their real photo. */
    private Node avatar(LeaderboardEntry entry, double size) {
        StackPane holder = new StackPane();
        holder.setMinSize(size, size);
        holder.setPrefSize(size, size);
        holder.setMaxSize(size, size);

        Label initials = new Label(entry.anonymous() && !entry.isViewer()
                ? "?" : initialsOf(entry.displayName()));
        initials.getStyleClass().addAll("avatar-initials", size <= 44 ? "avatar-initials-sm" : "");
        initials.setMinSize(size, size);
        initials.setPrefSize(size, size);
        initials.setAlignment(Pos.CENTER);
        holder.getChildren().add(initials);

        if (!entry.anonymous() || entry.isViewer()) {
            PhotoCache.getPhotoAsync(entry.donorId(), photo -> {
                if (photo == null) return;
                ImageView view = new ImageView(photo);
                view.setFitWidth(size);
                view.setFitHeight(size);
                view.setPreserveRatio(false);
                view.setClip(Avatars.squareClip(size));
                holder.getChildren().setAll(view);
            });
        }
        return holder;
    }

    private static String initialsOf(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        return parts.length == 1
                ? parts[0].substring(0, 1).toUpperCase()
                : (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    private Node iconText(String iconPath, String text) {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(text);
        label.getStyleClass().add("match-card-detail");
        row.getChildren().addAll(new Group(Icons.icon(iconPath, 11, "match-card-icon")), label);
        return row;
    }

    private Node note(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-head-sub");
        label.setWrapText(true);
        return label;
    }
}
