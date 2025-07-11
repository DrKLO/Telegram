package me.telegraphy.android;

import android.view.View;
import android.widget.FrameLayout;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;

import androidx.recyclerview.widget.RecyclerView;

import me.telegraphy.android.ui.Components.BackupImageView;
import me.telegraphy.android.ui.Components.RecyclerListView;
import me.telegraphy.android.ui.Cells.ProfileActionsCell;
import me.telegraphy.android.ui.Cells.ProfileBotCell;
import me.telegraphy.android.ui.Cells.ProfileBusinessCell;
import me.telegraphy.android.ui.Cells.ProfileChannelCell;
import me.telegraphy.android.ui.Cells.ProfileCoverCell;
import me.telegraphy.android.ui.Cells.ProfileDefaultCell;
import me.telegraphy.android.ui.Cells.ProfileGiftCell;
import me.telegraphy.android.ui.Cells.ProfileGroupCell;
import me.telegraphy.android.ui.Cells.ProfileHeaderCell;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;


/**
 * Holder mejorado para referencias de vistas del perfil con soporte completo
 * para animaciones de ProfileGiftCell y gestión de estados.
 * Mantiene referencias centralizadas a todas las vistas que necesitan ser animadas.
 * Soporte agregado para Stories tipo Instagram.
 *
 * @author Adrian Gainza Huepp
 */
public class ProfileViewHolder implements ProfileGiftCell.GiftAnimationListener, ProfileCoverCell.StoriesListener {

    // Contenedores principales
    public FrameLayout cardContainer;
    public RecyclerListView listView;

    // Componentes de encabezado
    public ProfileHeaderCell headerCell;
    public BackupImageView avatarView;
    public View nameTextView;
    public View statusTextView;
    public View bioTextView;

    // Botones de acción
    public ProfileActionsCell actionsCell;
    public View messageButton;
    public View callButton;
    public View videoCallButton;
    public View moreButton;

    // Componentes de portada (para canales/grupos)
    public BackupImageView coverImageView;
    public ProfileCoverCell coverCell;
    public ProfileBotCell botCell;
    public ProfileBusinessCell businessCell;
    public ProfileChannelCell channelCell;
    public ProfileGroupCell groupCell;
    public ProfileGiftCell giftCell;
    public ProfileDefaultCell defaultCell;

    // Elementos flotantes (para perfiles de regalo)
    public View[] floatingIcons;
    public View premiumBadge;
    public View verificationBadge;

    // Elementos de fondo
    public View backgroundGradient;
    public View shadowView;

    // Stories Support
    public RecyclerView storiesRecyclerView;
    public View storiesContainer;
    public View storiesIndicator;
    public View addStoryButton;
    private boolean isStoriesExpanded = false;
    private AnimatorSet storiesAnimator;

    public List<ProfileGiftCell> allGiftCells = new ArrayList<>();

    private ProfileGiftCell currentExpandedGiftCell;
    private boolean isAnyGiftExpanded = false;

    private GiftAnimationStateListener stateListener;
    private StoriesStateListener storiesStateListener;

    public ProfileViewHolder() {
    }


    public void initializeCoverViews(ProfileCoverCell coverCell) {
        this.coverCell = coverCell;
        if (coverCell != null) {
            // Assuming ProfileCoverCell has a method like getCoverImageView()
            // this.coverImageView = coverCell.getCoverImageView(); // Example
            coverCell.setStoriesListener(this);
        }
    }

    /**
     * Inicializa las vistas de Stories
     */
    public void initializeStoriesViews(RecyclerView storiesRecyclerView, View storiesContainer,
                                     View storiesIndicator, View addStoryButton) {
        this.storiesRecyclerView = storiesRecyclerView;
        this.storiesContainer = storiesContainer;
        this.storiesIndicator = storiesIndicator;
        this.addStoryButton = addStoryButton;

        // Configurar click listener para expandir/contraer stories
        if (storiesIndicator != null) {
            storiesIndicator.setOnClickListener(v -> toggleStoriesExpansion());
        }
    }

    public void initializeFloatingElements(View[] floatingIcons, View premiumBadge, View verificationBadge) {
        this.floatingIcons = floatingIcons;
        this.premiumBadge = premiumBadge;
        this.verificationBadge = verificationBadge;
    }

    public View[] getContentViewsForFade() {
        List<View> views = new ArrayList<>();
        if (coverCell != null) views.add(coverCell);
        if (defaultCell != null) views.add(defaultCell);
        if (botCell != null) views.add(botCell);
        if (businessCell != null) views.add(businessCell);
        if (channelCell != null) views.add(channelCell);
        if (groupCell != null) views.add(groupCell);
        if (giftCell != null) views.add(giftCell);

        if (nameTextView != null) views.add(nameTextView);
        if (statusTextView != null) views.add(statusTextView);
        if (bioTextView != null) views.add(bioTextView);

        // Agregar stories container a las vistas que pueden hacer fade
        if (storiesContainer != null) views.add(storiesContainer);

        return views.toArray(new View[0]);
    }

    public View[] getFloatingElements() {
        List<View> elements = new ArrayList<>();
        if (floatingIcons != null) {
            for (View icon : floatingIcons) {
                if (icon != null) elements.add(icon);
            }
        }
        if (premiumBadge != null) elements.add(premiumBadge);
        if (verificationBadge != null) elements.add(verificationBadge);
        return elements.toArray(new View[0]);
    }

    public BackupImageView getPrimaryAvatar() {
        return avatarView;
    }

    public FrameLayout getCardContainer() {
        return cardContainer;
    }

    public boolean hasHeaderViews() {
        return headerCell != null && avatarView != null;
    }

    public boolean hasActionsViews() {
        return actionsCell != null;
    }

    public boolean hasCoverViews() {
        return coverCell != null && coverImageView != null;
    }

    public boolean hasFloatingElements() {
        return (floatingIcons != null && floatingIcons.length > 0) || premiumBadge != null || verificationBadge != null;
    }

    /**
     * Verifica si hay Stories disponibles
     */
    public boolean hasStoriesViews() {
        return storiesRecyclerView != null && storiesContainer != null;
    }

    /**
     * Verifica si las Stories están expandidas
     */
    public boolean isStoriesExpanded() {
        return isStoriesExpanded;
    }

    /**
     * Expande o contrae las Stories con animación
     */
    public void toggleStoriesExpansion() {
        if (storiesContainer == null) return;

        isStoriesExpanded = !isStoriesExpanded;

        // Cancelar animación anterior si existe
        if (storiesAnimator != null) {
            storiesAnimator.cancel();
        }

        // Crear animación de expansión/contracción
        storiesAnimator = new AnimatorSet();

        if (isStoriesExpanded) {
            expandStories();
        } else {
            collapseStories();
        }
    }

    /**
     * Expande las Stories
     */
    private void expandStories() {
        if (storiesContainer == null) return;

        // Hacer visible el container
        storiesContainer.setVisibility(View.VISIBLE);

        // Animación de altura
        ValueAnimator heightAnimator = ValueAnimator.ofFloat(0f, 1f);
        heightAnimator.addUpdateListener(animation -> {
            float progress = (Float) animation.getAnimatedValue();
            storiesContainer.setScaleY(progress);
            storiesContainer.setAlpha(progress);
        });

        // Animación de rotación del indicador
        ObjectAnimator rotationAnimator = null;
        if (storiesIndicator != null) {
            rotationAnimator = ObjectAnimator.ofFloat(storiesIndicator, "rotation", 0f, 180f);
        }

        storiesAnimator.setDuration(300);
        storiesAnimator.setInterpolator(new DecelerateInterpolator());

        if (rotationAnimator != null) {
            storiesAnimator.playTogether(heightAnimator, rotationAnimator);
        } else {
            storiesAnimator.play(heightAnimator);
        }

        storiesAnimator.start();

        // Notificar al listener
        if (storiesStateListener != null) {
            storiesStateListener.onStoriesExpanded();
        }
    }

    /**
     * Contrae las Stories
     */
    private void collapseStories() {
        if (storiesContainer == null) return;

        // Animación de altura
        ValueAnimator heightAnimator = ValueAnimator.ofFloat(1f, 0f);
        heightAnimator.addUpdateListener(animation -> {
            float progress = (Float) animation.getAnimatedValue();
            storiesContainer.setScaleY(progress);
            storiesContainer.setAlpha(progress);
        });

        // Animación de rotación del indicador
        ObjectAnimator rotationAnimator = null;
        if (storiesIndicator != null) {
            rotationAnimator = ObjectAnimator.ofFloat(storiesIndicator, "rotation", 180f, 0f);
        }

        storiesAnimator.setDuration(300);
        storiesAnimator.setInterpolator(new DecelerateInterpolator());

        if (rotationAnimator != null) {
            storiesAnimator.playTogether(heightAnimator, rotationAnimator);
        } else {
            storiesAnimator.play(heightAnimator);
        }

        // Ocultar al final de la animación
        storiesAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (storiesContainer != null) {
                    storiesContainer.setVisibility(View.GONE);
                }
            }
        });

        storiesAnimator.start();

        // Notificar al listener
        if (storiesStateListener != null) {
            storiesStateListener.onStoriesCollapsed();
        }
    }

    /**
     * Fuerza el colapso de las Stories sin animación
     */
    public void forceCollapseStories() {
        if (storiesAnimator != null) {
            storiesAnimator.cancel();
        }

        if (storiesContainer != null) {
            storiesContainer.setVisibility(View.GONE);
            storiesContainer.setScaleY(0f);
            storiesContainer.setAlpha(0f);
        }

        if (storiesIndicator != null) {
            storiesIndicator.setRotation(0f);
        }

        isStoriesExpanded = false;
    }

    public void registerGiftCell(ProfileGiftCell giftCellInstance) {
        if (giftCellInstance != null && !allGiftCells.contains(giftCellInstance)) {
            allGiftCells.add(giftCellInstance);
            giftCellInstance.setAnimationListener(this);
            if (this.giftCell == null) {
                this.giftCell = giftCellInstance;
            }
        }
    }

    public void unregisterGiftCell(ProfileGiftCell giftCellInstance) {
        if (giftCellInstance != null) {
            allGiftCells.remove(giftCellInstance);
            giftCellInstance.setAnimationListener(null);
            if (this.giftCell == giftCellInstance) {
                this.giftCell = allGiftCells.isEmpty() ? null : allGiftCells.get(0);
            }
            if (currentExpandedGiftCell == giftCellInstance) {
                currentExpandedGiftCell = null;
                isAnyGiftExpanded = false;
                if (stateListener != null) {
                    stateListener.onGiftAnimationEnd(giftCellInstance, ProfileGiftCell.AnimationState.COLLAPSED);
                }
            }
        }
    }

    public void collapseAllGiftCellsExcept(@Nullable ProfileGiftCell exception) {
        for (ProfileGiftCell cell : allGiftCells) {
            if (cell != null && cell != exception && cell.isExpanded()) { // Assuming isExpanded() exists
                cell.collapseCell(); // Assuming collapseCell() exists
            }
        }
        if (exception == null || (currentExpandedGiftCell != null && currentExpandedGiftCell != exception)) {
            // Only reset if collapsing all or if the current expanded cell is not the exception
            // This logic might need refinement based on desired behavior when an exception is provided
        }
    }

    public void collapseAllGiftCells() {
        collapseAllGiftCellsExcept(null);
    }

    public void forceCollapseAllGiftCells() {
        for (ProfileGiftCell cell : allGiftCells) {
            if (cell != null) cell.forceCollapse();
        }
        currentExpandedGiftCell = null;
        isAnyGiftExpanded = false;
    }

    public boolean hasExpandedGiftCells() {
        return isAnyGiftExpanded;
    }

    @Nullable
    public ProfileGiftCell getCurrentExpandedGiftCell() {
        return currentExpandedGiftCell;
    }

    public List<ProfileGiftCell> getAllGiftCells() {
        return new ArrayList<>(allGiftCells);
    }

    public void updateGiftCellsTheme() {
        for (ProfileGiftCell cell : allGiftCells) {
            if (cell != null) cell.updateTheme();
        }
    }

    public void setGiftAnimationStateListener(GiftAnimationStateListener listener) {
        this.stateListener = listener;
    }

    public void setStoriesStateListener(StoriesStateListener listener) {
        this.storiesStateListener = listener;
    }

    @Override
    public void onAnimationStart(ProfileGiftCell cell, ProfileGiftCell.AnimationState state) {
        if (state == ProfileGiftCell.AnimationState.EXPANDING) {
            collapseAllGiftCellsExcept(cell);
            currentExpandedGiftCell = cell;
            isAnyGiftExpanded = true;
        }
        if (stateListener != null) {
            stateListener.onGiftAnimationStart(cell, state);
        }
    }

    @Override
    public void onAnimationEnd(ProfileGiftCell cell, ProfileGiftCell.AnimationState state) {
        if (state == ProfileGiftCell.AnimationState.COLLAPSED) {
            if (currentExpandedGiftCell == cell) {
                currentExpandedGiftCell = null;
                isAnyGiftExpanded = false;
            }
        } else if (state == ProfileGiftCell.AnimationState.EXPANDED) {
            currentExpandedGiftCell = cell;
            isAnyGiftExpanded = true;
        }
        if (stateListener != null) {
            stateListener.onGiftAnimationEnd(cell, state);
        }
    }

    @Override
    public void onGiftClicked(int giftIndex) {
        if (stateListener instanceof ExtendedGiftAnimationStateListener) {
            ((ExtendedGiftAnimationStateListener) stateListener).onGiftItemClicked(giftIndex, currentExpandedGiftCell);
        }
    }

    @Override
    public void onStateChanged(ProfileGiftCell.AnimationState newState) {
         if (stateListener instanceof ExtendedGiftAnimationStateListener) {
            ((ExtendedGiftAnimationStateListener) stateListener).onGiftCellStateChanged(newState, currentExpandedGiftCell);
        }
    }

    @Override
    public void onAnimationUpdate(ProfileGiftCell cell, float progress) {
        if (stateListener != null) {
            stateListener.onGiftAnimationUpdate(cell, progress);
        }
    }

    // Implementación de ProfileCoverCell.StoriesListener
    @Override
    public void onStoryClicked(int position) {
        if (storiesStateListener != null) {
            storiesStateListener.onStoryClicked(position);
        }
    }

    @Override
    public void onStoryLongClicked(int position) {
        if (storiesStateListener != null) {
            storiesStateListener.onStoryLongClicked(position);
        }
    }

    @Override
    public void onAddStoryClicked() {
        if (storiesStateListener != null) {
            storiesStateListener.onAddStoryClicked();
        }
    }

    @Override
    public void onStoriesScrolled(int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (storiesStateListener != null) {
            storiesStateListener.onStoriesScrolled(firstVisibleItem, visibleItemCount, totalItemCount);
        }
    }

    public void reset() {
        cardContainer = null;
        listView = null;
        headerCell = null;
        avatarView = null;
        nameTextView = null;
        statusTextView = null;
        bioTextView = null;
        actionsCell = null;
        messageButton = null;
        callButton = null;
        videoCallButton = null;
        moreButton = null;
        coverCell = null;
        coverImageView = null;
        botCell = null;
        businessCell = null;
        channelCell = null;
        groupCell = null;
        giftCell = null;
        floatingIcons = null;
        premiumBadge = null;
        verificationBadge = null;
        backgroundGradient = null;
        shadowView = null;

        // Reset Stories
        if (storiesAnimator != null) {
            storiesAnimator.cancel();
            storiesAnimator = null;
        }
        storiesRecyclerView = null;
        storiesContainer = null;
        storiesIndicator = null;
        addStoryButton = null;
        isStoriesExpanded = false;

        for (ProfileGiftCell cell : allGiftCells) {
            if (cell != null) cell.setAnimationListener(null);
        }
        allGiftCells.clear();
        currentExpandedGiftCell = null;
        isAnyGiftExpanded = false;
        stateListener = null;
        storiesStateListener = null;
    }

    public void applyContentTranslationY(float translationY) {
        for (View view : getContentViewsForFade()) {
            if (view != null) view.setTranslationY(translationY);
        }
    }

    public void applyContentAlpha(float alpha) {
        for (View view : getContentViewsForFade()) {
            if (view != null) view.setAlpha(alpha);
        }
    }

    public void applyFloatingElementsVisibility(int visibility) {
        for (View view : getFloatingElements()) {
            if (view != null) view.setVisibility(visibility);
        }
    }

    public void applyFloatingElementsScale(float scale) {
        for (View view : getFloatingElements()) {
            if (view != null) {
                view.setScaleX(scale);
                view.setScaleY(scale);
            }
        }
    }

    /**
     * Aplica animaciones específicas a las Stories
     */
    public void applyStoriesTranslationY(float translationY) {
        if (storiesContainer != null) {
            storiesContainer.setTranslationY(translationY);
        }
    }

    public void applyStoriesAlpha(float alpha) {
        if (storiesContainer != null) {
            storiesContainer.setAlpha(alpha);
        }
    }

    public void applyStoriesScale(float scale) {
        if (storiesContainer != null) {
            storiesContainer.setScaleX(scale);
            storiesContainer.setScaleY(scale);
        }
    }

    public void cleanup() {
        reset();
    }

    public interface GiftAnimationStateListener {
        void onGiftAnimationStart(ProfileGiftCell cell, ProfileGiftCell.AnimationState state);
        void onGiftAnimationEnd(ProfileGiftCell cell, ProfileGiftCell.AnimationState state);
        default void onGiftAnimationUpdate(ProfileGiftCell cell, float progress) {}
    }

    public interface ExtendedGiftAnimationStateListener extends GiftAnimationStateListener {
        void onGiftItemClicked(int giftIndex, ProfileGiftCell cell);
        void onGiftCellStateChanged(ProfileGiftCell.AnimationState newState, ProfileGiftCell cell);
    }

    /**
     * Listener para eventos de Stories
     */
    public interface StoriesStateListener {
        void onStoriesExpanded();
        void onStoriesCollapsed();
        void onStoryClicked(int position);
        void onStoryLongClicked(int position);
        void onAddStoryClicked();
        void onStoriesScrolled(int firstVisibleItem, int visibleItemCount, int totalItemCount);
    }
}
