/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.telegram.messenger.Animation;

import android.view.animation.Interpolator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public final class AnimatorSet10 extends Animator10 {

    private ArrayList<Animator10> mPlayingSet = new ArrayList<>();
    private HashMap<Animator10, Node> mNodeMap = new HashMap<>();
    private ArrayList<Node> mNodes = new ArrayList<>();
    private ArrayList<Node> mSortedNodes = new ArrayList<>();
    private boolean mNeedsSort = true;
    private AnimatorSetListener mSetListener = null;
    boolean mTerminated = false;
    private boolean mStarted = false;
    private long mStartDelay = 0;
    private ValueAnimator mDelayAnim = null;
    private long mDuration = -1;
    private Interpolator mInterpolator = null;

    public void playTogether(Animator10... items) {
        if (items != null) {
            mNeedsSort = true;
            Builder builder = play(items[0]);
            for (int i = 1; i < items.length; ++i) {
                builder.with(items[i]);
            }
        }
    }

    public void playTogether(Collection<Animator10> items) {
        if (items != null && items.size() > 0) {
            mNeedsSort = true;
            Builder builder = null;
            for (Animator10 anim : items) {
                if (builder == null) {
                    builder = play(anim);
                } else {
                    builder.with(anim);
                }
            }
        }
    }

    public void playSequentially(Animator10... items) {
        if (items != null) {
            mNeedsSort = true;
            if (items.length == 1) {
                play(items[0]);
            } else {
                for (int i = 0; i < items.length - 1; ++i) {
                    play(items[i]).before(items[i+1]);
                }
            }
        }
    }

    public void playSequentially(List<Animator10> items) {
        if (items != null && items.size() > 0) {
            mNeedsSort = true;
            if (items.size() == 1) {
                play(items.get(0));
            } else {
                for (int i = 0; i < items.size() - 1; ++i) {
                    play(items.get(i)).before(items.get(i+1));
                }
            }
        }
    }

    public ArrayList<Animator10> getChildAnimations() {
        ArrayList<Animator10> childList = new ArrayList<>();
        for (Node node : mNodes) {
            childList.add(node.animation);
        }
        return childList;
    }

    @Override
    public void setTarget(Object target) {
        for (Node node : mNodes) {
            Animator10 animation = node.animation;
            if (animation instanceof AnimatorSet10) {
                animation.setTarget(target);
            } else if (animation instanceof ObjectAnimator10) {
                animation.setTarget(target);
            }
        }
    }

    @Override
    public void setInterpolator(Interpolator interpolator) {
        mInterpolator = interpolator;
    }

    @Override
    public Interpolator getInterpolator() {
        return mInterpolator;
    }

    public Builder play(Animator10 anim) {
        if (anim != null) {
            mNeedsSort = true;
            return new Builder(anim);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void cancel() {
        mTerminated = true;
        if (isStarted()) {
            ArrayList<AnimatorListener> tmpListeners = null;
            if (mListeners != null) {
                tmpListeners = (ArrayList<AnimatorListener>) mListeners.clone();
                for (AnimatorListener listener : tmpListeners) {
                    listener.onAnimationCancel(this);
                }
            }
            if (mDelayAnim != null && mDelayAnim.isRunning()) {
                mDelayAnim.cancel();
            } else  if (mSortedNodes.size() > 0) {
                for (Node node : mSortedNodes) {
                    node.animation.cancel();
                }
            }
            if (tmpListeners != null) {
                for (AnimatorListener listener : tmpListeners) {
                    listener.onAnimationEnd(this);
                }
            }
            mStarted = false;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void end() {
        mTerminated = true;
        if (isStarted()) {
            if (mSortedNodes.size() != mNodes.size()) {
                // hasn't been started yet - sort the nodes now, then end them
                sortNodes();
                for (Node node : mSortedNodes) {
                    if (mSetListener == null) {
                        mSetListener = new AnimatorSetListener(this);
                    }
                    node.animation.addListener(mSetListener);
                }
            }
            if (mDelayAnim != null) {
                mDelayAnim.cancel();
            }
            if (mSortedNodes.size() > 0) {
                for (Node node : mSortedNodes) {
                    node.animation.end();
                }
            }
            if (mListeners != null) {
                ArrayList<AnimatorListener> tmpListeners = (ArrayList<AnimatorListener>) mListeners.clone();
                for (AnimatorListener listener : tmpListeners) {
                    listener.onAnimationEnd(this);
                }
            }
            mStarted = false;
        }
    }

    @Override
    public boolean isRunning() {
        for (Node node : mNodes) {
            if (node.animation.isRunning()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isStarted() {
        return mStarted;
    }

    @Override
    public long getStartDelay() {
        return mStartDelay;
    }

    @Override
    public void setStartDelay(long startDelay) {
        mStartDelay = startDelay;
    }

    @Override
    public long getDuration() {
        return mDuration;
    }

    @Override
    public AnimatorSet10 setDuration(long duration) {
        if (duration < 0) {
            throw new IllegalArgumentException("duration must be a value of zero or greater");
        }
        mDuration = duration;
        return this;
    }

    @Override
    public void setupStartValues() {
        for (Node node : mNodes) {
            node.animation.setupStartValues();
        }
    }

    @Override
    public void setupEndValues() {
        for (Node node : mNodes) {
            node.animation.setupEndValues();
        }
    }

    @Override
    public void pause() {
        boolean previouslyPaused = mPaused;
        super.pause();
        if (!previouslyPaused && mPaused) {
            if (mDelayAnim != null) {
                mDelayAnim.pause();
            } else {
                for (Node node : mNodes) {
                    node.animation.pause();
                }
            }
        }
    }

    @Override
    public void resume() {
        boolean previouslyPaused = mPaused;
        super.resume();
        if (previouslyPaused && !mPaused) {
            if (mDelayAnim != null) {
                mDelayAnim.resume();
            } else {
                for (Node node : mNodes) {
                    node.animation.resume();
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void start() {
        mTerminated = false;
        mStarted = true;
        mPaused = false;

        if (mDuration >= 0) {
            for (Node node : mNodes) {
                node.animation.setDuration(mDuration);
            }
        }
        if (mInterpolator != null) {
            for (Node node : mNodes) {
                node.animation.setInterpolator(mInterpolator);
            }
        }

        sortNodes();

        int numSortedNodes = mSortedNodes.size();
        for (Node node : mSortedNodes) {
            ArrayList<AnimatorListener> oldListeners = node.animation.getListeners();
            if (oldListeners != null && oldListeners.size() > 0) {
                final ArrayList<AnimatorListener> clonedListeners = new
                        ArrayList<>(oldListeners);

                for (AnimatorListener listener : clonedListeners) {
                    if (listener instanceof DependencyListener ||
                            listener instanceof AnimatorSetListener) {
                        node.animation.removeListener(listener);
                    }
                }
            }
        }

        final ArrayList<Node> nodesToStart = new ArrayList<>();
        for (Node node : mSortedNodes) {
            if (mSetListener == null) {
                mSetListener = new AnimatorSetListener(this);
            }
            if (node.dependencies == null || node.dependencies.size() == 0) {
                nodesToStart.add(node);
            } else {
                int numDependencies = node.dependencies.size();
                for (int j = 0; j < numDependencies; ++j) {
                    Dependency dependency = node.dependencies.get(j);
                    dependency.node.animation.addListener(
                            new DependencyListener(this, node, dependency.rule));
                }
                node.tmpDependencies = (ArrayList<Dependency>) node.dependencies.clone();
            }
            node.animation.addListener(mSetListener);
        }

        if (mStartDelay <= 0) {
            for (Node node : nodesToStart) {
                node.animation.start();
                mPlayingSet.add(node.animation);
            }
        } else {
            mDelayAnim = ValueAnimator.ofFloat(0f, 1f);
            mDelayAnim.setDuration(mStartDelay);
            mDelayAnim.addListener(new AnimatorListenerAdapter10() {
                boolean canceled = false;
                public void onAnimationCancel(Animator10 anim) {
                    canceled = true;
                }
                public void onAnimationEnd(Animator10 anim) {
                    if (!canceled) {
                        int numNodes = nodesToStart.size();
                        for (Node node : nodesToStart) {
                            node.animation.start();
                            mPlayingSet.add(node.animation);
                        }
                    }
                    mDelayAnim = null;
                }
            });
            mDelayAnim.start();
        }
        if (mListeners != null) {
            ArrayList<AnimatorListener> tmpListeners =
                    (ArrayList<AnimatorListener>) mListeners.clone();
            int numListeners = tmpListeners.size();
            for (AnimatorListener tmpListener : tmpListeners) {
                tmpListener.onAnimationStart(this);
            }
        }
        if (mNodes.size() == 0 && mStartDelay == 0) {
            mStarted = false;
            if (mListeners != null) {
                ArrayList<AnimatorListener> tmpListeners =
                        (ArrayList<AnimatorListener>) mListeners.clone();
                int numListeners = tmpListeners.size();
                for (AnimatorListener tmpListener : tmpListeners) {
                    tmpListener.onAnimationEnd(this);
                }
            }
        }
    }

    @Override
    public AnimatorSet10 clone() {
        final AnimatorSet10 anim = (AnimatorSet10) super.clone();

        anim.mNeedsSort = true;
        anim.mTerminated = false;
        anim.mStarted = false;
        anim.mPlayingSet = new ArrayList<>();
        anim.mNodeMap = new HashMap<>();
        anim.mNodes = new ArrayList<>();
        anim.mSortedNodes = new ArrayList<>();

        HashMap<Node, Node> nodeCloneMap = new HashMap<>();
        for (Node node : mNodes) {
            Node nodeClone = node.clone();
            nodeCloneMap.put(node, nodeClone);
            anim.mNodes.add(nodeClone);
            anim.mNodeMap.put(nodeClone.animation, nodeClone);
            nodeClone.dependencies = null;
            nodeClone.tmpDependencies = null;
            nodeClone.nodeDependents = null;
            nodeClone.nodeDependencies = null;
            ArrayList<AnimatorListener> cloneListeners = nodeClone.animation.getListeners();
            if (cloneListeners != null) {
                ArrayList<AnimatorListener> listenersToRemove = null;
                for (AnimatorListener listener : cloneListeners) {
                    if (listener instanceof AnimatorSetListener) {
                        if (listenersToRemove == null) {
                            listenersToRemove = new ArrayList<>();
                        }
                        listenersToRemove.add(listener);
                    }
                }
                if (listenersToRemove != null) {
                    for (AnimatorListener listener : listenersToRemove) {
                        cloneListeners.remove(listener);
                    }
                }
            }
        }
        for (Node node : mNodes) {
            Node nodeClone = nodeCloneMap.get(node);
            if (node.dependencies != null) {
                for (Dependency dependency : node.dependencies) {
                    Node clonedDependencyNode = nodeCloneMap.get(dependency.node);
                    Dependency cloneDependency = new Dependency(clonedDependencyNode, dependency.rule);
                    nodeClone.addDependency(cloneDependency);
                }
            }
        }
        return anim;
    }

    private static class DependencyListener implements AnimatorListener {

        private AnimatorSet10 mAnimatorSet;
        private Node mNode;
        private int mRule;

        public DependencyListener(AnimatorSet10 animatorSet, Node node, int rule) {
            this.mAnimatorSet = animatorSet;
            this.mNode = node;
            this.mRule = rule;
        }

        public void onAnimationCancel(Animator10 animation) {

        }

        public void onAnimationEnd(Animator10 animation) {
            if (mRule == Dependency.AFTER) {
                startIfReady(animation);
            }
        }

        public void onAnimationRepeat(Animator10 animation) {

        }

        public void onAnimationStart(Animator10 animation) {
            if (mRule == Dependency.WITH) {
                startIfReady(animation);
            }
        }

        private void startIfReady(Animator10 dependencyAnimation) {
            if (mAnimatorSet.mTerminated) {
                return;
            }
            Dependency dependencyToRemove = null;
            int numDependencies = mNode.tmpDependencies.size();
            for (int i = 0; i < numDependencies; ++i) {
                Dependency dependency = mNode.tmpDependencies.get(i);
                if (dependency.rule == mRule && dependency.node.animation == dependencyAnimation) {
                    dependencyToRemove = dependency;
                    dependencyAnimation.removeListener(this);
                    break;
                }
            }
            mNode.tmpDependencies.remove(dependencyToRemove);
            if (mNode.tmpDependencies.size() == 0) {
                mNode.animation.start();
                mAnimatorSet.mPlayingSet.add(mNode.animation);
            }
        }
    }

    private class AnimatorSetListener implements AnimatorListener {

        private AnimatorSet10 mAnimatorSet;

        AnimatorSetListener(AnimatorSet10 animatorSet) {
            mAnimatorSet = animatorSet;
        }

        public void onAnimationCancel(Animator10 animation) {
            if (!mTerminated) {
                if (mPlayingSet.size() == 0) {
                    if (mListeners != null) {
                        int numListeners = mListeners.size();
                        for (AnimatorListener mListener : mListeners) {
                            mListener.onAnimationCancel(mAnimatorSet);
                        }
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        public void onAnimationEnd(Animator10 animation) {
            animation.removeListener(this);
            mPlayingSet.remove(animation);
            Node animNode = mAnimatorSet.mNodeMap.get(animation);
            animNode.done = true;
            if (!mTerminated) {
                ArrayList<Node> sortedNodes = mAnimatorSet.mSortedNodes;
                boolean allDone = true;
                int numSortedNodes = sortedNodes.size();
                for (Node sortedNode : sortedNodes) {
                    if (!sortedNode.done) {
                        allDone = false;
                        break;
                    }
                }
                if (allDone) {
                    if (mListeners != null) {
                        ArrayList<AnimatorListener> tmpListeners =
                                (ArrayList<AnimatorListener>) mListeners.clone();
                        int numListeners = tmpListeners.size();
                        for (AnimatorListener tmpListener : tmpListeners) {
                            tmpListener.onAnimationEnd(mAnimatorSet);
                        }
                    }
                    mAnimatorSet.mStarted = false;
                    mAnimatorSet.mPaused = false;
                }
            }
        }

        public void onAnimationRepeat(Animator10 animation) {

        }

        public void onAnimationStart(Animator10 animation) {

        }
    }

    private void sortNodes() {
        if (mNeedsSort) {
            mSortedNodes.clear();
            ArrayList<Node> roots = new ArrayList<>();
            int numNodes = mNodes.size();
            for (Node node : mNodes) {
                if (node.dependencies == null || node.dependencies.size() == 0) {
                    roots.add(node);
                }
            }
            ArrayList<Node> tmpRoots = new ArrayList<>();
            while (roots.size() > 0) {
                int numRoots = roots.size();
                for (Node root : roots) {
                    mSortedNodes.add(root);
                    if (root.nodeDependents != null) {
                        int numDependents = root.nodeDependents.size();
                        for (int j = 0; j < numDependents; ++j) {
                            Node node = root.nodeDependents.get(j);
                            node.nodeDependencies.remove(root);
                            if (node.nodeDependencies.size() == 0) {
                                tmpRoots.add(node);
                            }
                        }
                    }
                }
                roots.clear();
                roots.addAll(tmpRoots);
                tmpRoots.clear();
            }
            mNeedsSort = false;
            if (mSortedNodes.size() != mNodes.size()) {
                throw new IllegalStateException("Circular dependencies cannot exist in AnimatorSet");
            }
        } else {
            int numNodes = mNodes.size();
            for (Node node : mNodes) {
                if (node.dependencies != null && node.dependencies.size() > 0) {
                    int numDependencies = node.dependencies.size();
                    for (int j = 0; j < numDependencies; ++j) {
                        Dependency dependency = node.dependencies.get(j);
                        if (node.nodeDependencies == null) {
                            node.nodeDependencies = new ArrayList<>();
                        }
                        if (!node.nodeDependencies.contains(dependency.node)) {
                            node.nodeDependencies.add(dependency.node);
                        }
                    }
                }
                node.done = false;
            }
        }
    }

    private static class Dependency {
        static final int WITH = 0;
        static final int AFTER = 1;
        public Node node;
        public int rule;

        public Dependency(Node node, int rule) {
            this.node = node;
            this.rule = rule;
        }
    }

    private static class Node implements Cloneable {
        public Animator10 animation;
        public ArrayList<Dependency> dependencies = null;
        public ArrayList<Dependency> tmpDependencies = null;
        public ArrayList<Node> nodeDependencies = null;
        public ArrayList<Node> nodeDependents = null;
        public boolean done = false;

        public Node(Animator10 animation) {
            this.animation = animation;
        }

        public void addDependency(Dependency dependency) {
            if (dependencies == null) {
                dependencies = new ArrayList<>();
                nodeDependencies = new ArrayList<>();
            }
            dependencies.add(dependency);
            if (!nodeDependencies.contains(dependency.node)) {
                nodeDependencies.add(dependency.node);
            }
            Node dependencyNode = dependency.node;
            if (dependencyNode.nodeDependents == null) {
                dependencyNode.nodeDependents = new ArrayList<>();
            }
            dependencyNode.nodeDependents.add(this);
        }

        @Override
        public Node clone() {
            try {
                Node node = (Node) super.clone();
                node.animation = animation.clone();
                return node;
            } catch (CloneNotSupportedException e) {
               throw new AssertionError();
            }
        }
    }

    public class Builder {

        private Node mCurrentNode;

        Builder(Animator10 anim) {
            mCurrentNode = mNodeMap.get(anim);
            if (mCurrentNode == null) {
                mCurrentNode = new Node(anim);
                mNodeMap.put(anim, mCurrentNode);
                mNodes.add(mCurrentNode);
            }
        }

        public Builder with(Animator10 anim) {
            Node node = mNodeMap.get(anim);
            if (node == null) {
                node = new Node(anim);
                mNodeMap.put(anim, node);
                mNodes.add(node);
            }
            Dependency dependency = new Dependency(mCurrentNode, Dependency.WITH);
            node.addDependency(dependency);
            return this;
        }

        public Builder before(Animator10 anim) {
            Node node = mNodeMap.get(anim);
            if (node == null) {
                node = new Node(anim);
                mNodeMap.put(anim, node);
                mNodes.add(node);
            }
            Dependency dependency = new Dependency(mCurrentNode, Dependency.AFTER);
            node.addDependency(dependency);
            return this;
        }

        public Builder after(Animator10 anim) {
            Node node = mNodeMap.get(anim);
            if (node == null) {
                node = new Node(anim);
                mNodeMap.put(anim, node);
                mNodes.add(node);
            }
            Dependency dependency = new Dependency(node, Dependency.AFTER);
            mCurrentNode.addDependency(dependency);
            return this;
        }

        public Builder after(long delay) {
            ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(delay);
            after(anim);
            return this;
        }
    }
}
