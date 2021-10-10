/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import android.util.SparseArray;
import android.view.View;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LauncherBindableItemsContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Launcher model Callbacks for rendering taskbar.
 */
public class TaskbarModelCallbacks implements
        BgDataModel.Callbacks, LauncherBindableItemsContainer {

    private final SparseArray<ItemInfo> mHotseatItems = new SparseArray<>();
    private List<ItemInfo> mPredictedItems = Collections.emptyList();

    private final TaskbarActivityContext mContext;
    private final TaskbarView mContainer;

    // Initialized in init.
    private TaskbarControllers mControllers;

    private boolean mBindInProgress = false;

    public TaskbarModelCallbacks(
            TaskbarActivityContext context, TaskbarView container) {
        mContext = context;
        mContainer = container;
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
    }

    @Override
    public void startBinding() {
        mBindInProgress = true;
        mHotseatItems.clear();
        mPredictedItems = Collections.emptyList();
    }

    @Override
    public void finishBindingItems(IntSet pagesBoundFirst) {
        mBindInProgress = false;
        commitItemsToUI();
    }

    @Override
    public void bindAppsAdded(IntArray newScreens, ArrayList<ItemInfo> addNotAnimated,
            ArrayList<ItemInfo> addAnimated) {
        boolean add1 = handleItemsAdded(addNotAnimated);
        boolean add2 = handleItemsAdded(addAnimated);
        if (add1 || add2) {
            commitItemsToUI();
        }
    }

    @Override
    public void bindItems(List<ItemInfo> shortcuts, boolean forceAnimateIcons) {
        if (handleItemsAdded(shortcuts)) {
            commitItemsToUI();
        }
    }

    private boolean handleItemsAdded(List<ItemInfo> items) {
        boolean modified = false;
        for (ItemInfo item : items) {
            if (item.container == Favorites.CONTAINER_HOTSEAT) {
                mHotseatItems.put(item.screenId, item);
                modified = true;
            }
        }
        return modified;
    }


    @Override
    public void bindWorkspaceItemsChanged(List<WorkspaceItemInfo> updated) {
        updateWorkspaceItems(updated, mContext);
    }

    @Override
    public void bindRestoreItemsChange(HashSet<ItemInfo> updates) {
        updateRestoreItems(updates, mContext);
    }

    @Override
    public void mapOverItems(ItemOperator op) {
        final int itemCount = mContainer.getChildCount();
        for (int itemIdx = 0; itemIdx < itemCount; itemIdx++) {
            View item = mContainer.getChildAt(itemIdx);
            if (op.evaluate((ItemInfo) item.getTag(), item)) {
                return;
            }
        }
    }

    @Override
    public void bindWorkspaceComponentsRemoved(ItemInfoMatcher matcher) {
        if (handleItemsRemoved(matcher)) {
            commitItemsToUI();
        }
    }

    private boolean handleItemsRemoved(ItemInfoMatcher matcher) {
        boolean modified = false;
        for (int i = mHotseatItems.size() - 1; i >= 0; i--) {
            if (matcher.matchesInfo(mHotseatItems.valueAt(i))) {
                modified = true;
                mHotseatItems.removeAt(i);
            }
        }
        return modified;
    }

    @Override
    public void bindItemsModified(List<ItemInfo> items) {
        boolean removed = handleItemsRemoved(ItemInfoMatcher.ofItems(items));
        boolean added = handleItemsAdded(items);
        if (removed || added) {
            commitItemsToUI();
        }
    }

    @Override
    public void bindExtraContainerItems(FixedContainerItems item) {
        if (item.containerId == Favorites.CONTAINER_HOTSEAT_PREDICTION) {
            mPredictedItems = item.items;
            commitItemsToUI();
        }
    }

    private void commitItemsToUI() {
        if (mBindInProgress) {
            return;
        }

        ItemInfo[] hotseatItemInfos =
                new ItemInfo[mContext.getDeviceProfile().numShownHotseatIcons];
        int predictionSize = mPredictedItems.size();
        int predictionNextIndex = 0;

        boolean isHotseatEmpty = true;
        for (int i = 0; i < hotseatItemInfos.length; i++) {
            hotseatItemInfos[i] = mHotseatItems.get(i);
            if (hotseatItemInfos[i] == null && predictionNextIndex < predictionSize) {
                hotseatItemInfos[i] = mPredictedItems.get(predictionNextIndex);
                hotseatItemInfos[i].screenId = i;
                predictionNextIndex++;
            }
            if (hotseatItemInfos[i] != null) {
                isHotseatEmpty = false;
            }
        }
        mContainer.updateHotseatItems(hotseatItemInfos);

        mControllers.taskbarStashController.updateStateForFlag(
                TaskbarStashController.FLAG_STASHED_IN_APP_EMPTY, isHotseatEmpty);
        mControllers.taskbarStashController.applyState();
    }
}