<template>
  <div
    class="input-group nodefilters multiple-control-input-group"
    v-bind="$attrs"
  >
    <span v-if="showTitle" class="input-group-addon input-group-addon-title">{{
      $t("nodes")
    }}</span>
    <div class="input-group-btn input-btn-toggle">
      <button
        type="button"
        class="btn btn-default dropdown-toggle job_edit__node_filter__filter_select_dropdown"
        :class="{
          'btn-success': selectedFilterName,
          'btn-default': !selectedFilterName,
        }"
        data-toggle="dropdown"
        data-testid="nfi-toggle"
      >
        <span>{{ filterNameDisplay }}</span> <span class="caret"></span>
      </button>
      <ul class="dropdown-menu">
        <li>
          <node-filter-link
            node-filter-name=".*"
            node-filter=".*"
            :class="{
              active: '.*' === selectedFilterName,
              job_edit__node_filter__filter_select_all: true,
            }"
            @nodefilterclick="handleNodefilter"
          >
            <i class="fas fa-asterisk"></i>
            {{ $t("show.all.nodes") }}
          </node-filter-link>
        </li>

        <li class="divider"></li>

        <li v-if="selectedFilterName" class="dropdown-header">
          {{ $t("filter") }} <span>{{ filterNameDisplay }}</span>
        </li>
        <li v-if="canSaveFilter">
          <a href="#" @click="saveFilterModal = true">
            <i class="glyphicon glyphicon-plus"></i>
            {{ $t("save.filter.ellipsis") }}
          </a>
        </li>
        <li v-if="canDeleteFilter">
          <a href="#" @click="deleteFilterModal = true">
            <i class="glyphicon glyphicon-remove"></i>
            {{ $t("delete.this.filter") }}
          </a>
        </li>
        <li v-if="canSetDefaultFilter">
          <a href="#" @click="setDefaultFilter">
            <i class="glyphicon glyphicon-filter"></i>
            {{ $t("set.as.default.filter") }}
          </a>
        </li>
        <li v-if="canRemoveDefaultFilter">
          <a href="#" @click="removeDefaultFilter">
            <i class="glyphicon glyphicon-ban-circle"></i>
            {{ $t("remove.default.filter") }}
          </a>
        </li>

        <template
          v-if="
            nodeSummary && nodeSummary.filters && nodeSummary.filters.length > 0
          "
        >
          <li class="divider"></li>
          <li class="dropdown-header">{{ $t("saved.filters") }}</li>
          <li v-for="filter in nodeSummary.filters">
            <node-filter-link
              :node-filter-name="filter.filterName"
              :node-filter="filter.filter"
              @nodefilterclick="handleNodefilter"
            >
              <template v-if="selectedFilterName === filter.filterName" #suffix>
                <span>
                  <i class="fa fa-check"></i>
                </span>
              </template>
            </node-filter-link>
          </li>
        </template>
      </ul>
    </div>

    <input
      :id="filterFieldId"
      v-model="outputValue"
      type="search"
      :name="filterFieldName"
      class="schedJobNodeFilter form-control"
      :autofocus="autofocus"
      :placeholder="queryFieldPlaceholderText || $t('enter.a.node.filter')"
      data-testid="filter-input"
      @keydown.enter.prevent="doSearch"
      @blur="doSearch"
    />

    <div v-if="helpButton" class="input-group-btn input-btn-toggle">
      <btn id="filterSearchHelpBtn" tabindex="0" class="dropdown-toggle">
        <i class="glyphicon glyphicon-question-sign"></i>
      </btn>
    </div>
    <div class="input-group-btn">
      <btn
        :type="`${searchBtnType} btn-fill`"
        :disabled="!outputValue"
        class="node_filter__dosearch"
        @click="doSearch"
      >
        {{ $t("Search") }}
      </btn>
    </div>
  </div>

  <popover
    v-if="helpButton"
    target="#filterSearchHelpBtn"
    trigger="focus"
    placement="bottom"
  >
    <template #popover>
      <div class="help-block">
        <strong>{{ $t("select.nodes.by.name") }}:</strong>
        <p>
          <code>{{ $t("mynode1.mynode2") }}</code>
        </p>
        <p>
          {{ $t("this.will.select.both.nodes") }}
        </p>

        <strong>{{ $t("filter.nodes.by.attribute.value") }}:</strong>
        <ul>
          <li>
            {{ $t("include") }}:
            <code>{{ $t("attribute") }}: {{ $t("value") }}</code>
          </li>

          <li>
            {{ $t("exclude") }}:
            <code>!{{ $t("attribute") }}: {{ $t("value") }}</code>
          </li>
        </ul>

        <strong>{{ $t("use.regular.expressions") }}</strong>
        <p>
          <code>{{ $t("node.metadata.hostname") }}: dev(\d+).test.com</code>.
        </p>

        <strong>{{ $t("regex.syntax.checking") }}:</strong>
        <p>
          <code>{{ $t("attribute") }}: /regex/</code>
        </p>
      </div>
    </template>
  </popover>
  <modal v-model="saveFilterModal" :title="$t('save.node.filter')">
    <div>
      <div class="form-group">
        <label for="newFilterName" class="control-label col-sm-2">{{
          $t("name.prompt")
        }}</label>
        <div class="col-sm-10">
          <input
            id="newFilterName"
            v-model="newFilterName"
            type="text"
            class="form-control input-sm"
            data-testid="new-filter-name-input"
          />
        </div>
      </div>
      <div class="form-group">
        <label class="control-label col-sm-2">
          {{ $t("filter") }}
        </label>
        <div class="col-sm-10">
          <span class="form-control form-control-static">{{
            outputValue
          }}</span>
        </div>
      </div>
      <div v-if="saveFilterModalError" class="text-danger">
        {{ saveFilterModalError }}
      </div>
    </div>
    <template #footer>
      <div>
        <btn @click="saveFilterModal = false" data-testid="sfm-button-cancel">
          {{ $t("button.action.Cancel") }}
        </btn>
        <btn type="primary" @click="saveFilter" data-testid="sfm-button-save">
          {{ $t("save.filter.ellipsis") }}
        </btn>
      </div>
    </template>
  </modal>
  <modal v-model="deleteFilterModal" :title="$t('delete.saved.node.filter')">
    <div>
      <div class="modal-body">
        <div class="form-group">
          <label class="control-label col-sm-2">{{ $t("name.prompt") }}</label>
          <div class="col-sm-10">
            <span class="form-control form-control-static">{{
              selectedFilterName
            }}</span>
          </div>
        </div>
        <div v-if="selectedSavedFilter" class="form-group">
          <label class="control-label col-sm-2">
            {{ $t("filter") }}
          </label>

          <div class="col-sm-10">
            <span class="form-control form-control-static">{{
              selectedSavedFilter.filter
            }}</span>
          </div>
        </div>
      </div>
      <div class="modal-body">
        <span class="text-danger">{{ $t("delete.this.filter.confirm") }}</span>
      </div>
    </div>
    <template #footer>
      <div>
        <btn @click="deleteFilterModal = false">{{ $t("no") }}</btn>
        <btn type="danger" @click="deleteFilter">{{ $t("yes") }}</btn>
      </div>
    </template>
  </modal>
</template>
<script lang="ts">
import { getRundeckContext } from "../../../../library";
import {
  NodeFilterStore,
  ProjectFilters,
} from "../../../../library/stores/NodeFilterLocalstore";
import { defineComponent, ref, PropType } from "vue";
import NodeFilterLink from "./NodeFilterLink.vue";

export default defineComponent({
  name: "NodeFilterInput",
  components: {
    NodeFilterLink,
  },
  inheritAttrs: false,
  props: {
    modelValue: {
      type: [String, null] as PropType<string | null>,
      required: true,
    },
    showTitle: {
      type: Boolean,
      required: false,
      default: false,
    },
    autofocus: {
      type: Boolean,
      required: false,
      default: false,
    },
    helpButton: {
      type: Boolean,
      required: false,
      default: true,
    },
    searchBtnType: {
      type: String,
      required: false,
      default: "default",
    },
    filterName: {
      type: String,
      required: false,
      default: "",
    },
    project: {
      type: String,
      required: true,
    },
    filterFieldName: {
      type: String,
      required: false,
      default: "filter",
    },
    queryFieldPlaceholderText: {
      type: String,
      required: false,
      default: "",
    },
    filterFieldId: {
      type: String,
      required: false,
      default: "schedJobNodeFilter",
    },
    allowFilterDefault: {
      type: Boolean,
      required: false,
      default: false,
      description: "if true, allow setting/removing default filter"
    },
  },
  emits: ["filters-updated", "filter", "update:modelValue"],
  setup() {
    const outputValue = ref("");
    const selectedFilterName = ref("");
    const hideAll = ref(false);
    const saveFilterModal = ref(false);
    const saveFilterModalError = ref("");
    const newFilterName = ref("");
    const deleteFilterModal = ref(false);
    const nodeSummary = ref({} as ProjectFilters);
    const nodeFilterStore = new NodeFilterStore();
    const eventBus = getRundeckContext().eventBus;
    return {
      outputValue,
      selectedFilterName,
      hideAll,
      saveFilterModal,
      saveFilterModalError,
      newFilterName,
      deleteFilterModal,
      nodeSummary,
      nodeFilterStore,
      eventBus,
    };
  },
  computed: {
    filterNameDisplay() {
      return this.outputValue === ".*" || this.selectedFilterName === ".*"
        ? "All Nodes"
        : this.selectedFilterName;
    },
    canSaveFilter() {
      return !this.selectedFilterName && this.filterWithoutAll();
    },
    canDeleteFilter() {
      return this.selectedFilterName && this.selectedFilterName !== ".*";
    },
    canSetDefaultFilter() {
      return (
        this.nodeSummary &&
        this.allowFilterDefault &&
        this.selectedFilterName &&
        this.selectedFilterName !== this.nodeSummary.defaultFilter
      );
    },
    canRemoveDefaultFilter() {
      return (
        this.nodeSummary &&
        this.allowFilterDefault &&
        !this.selectedFilterName &&
        this.selectedFilterName === this.nodeSummary.defaultFilter
      );
    },
    matchedFilter() {
      if (this.outputValue && this.nodeSummary.filters) {
        const found = this.nodeSummary.filters.find(
          (a: any) => a.filter === this.outputValue,
        );
        if (found) {
          return found.filterName;
        }
      }
      if (this.outputValue && this.outputValue === ".*") {
        return this.outputValue;
      }
      return null;
    },
    selectedSavedFilter() {
      if (this.selectedFilterName && this.nodeSummary.filters) {
        const found = this.nodeSummary.filters.find(
          (a: any) => a.filterName === this.selectedFilterName,
        );
        if (found) {
          return found;
        }
      }
      return null;
    },
  },
  watch: {
    modelValue() {
      if (this.modelValue) {
        this.outputValue = this.modelValue;
      }
      if (
        this.selectedFilterName &&
        this.selectedFilterName !== this.matchedFilter
      ) {
        this.selectedFilterName = "";
      } else if (this.matchedFilter) {
        this.selectedFilterName = this.matchedFilter;
      }
    },
    filterName() {
      this.selectedFilterName = this.filterName;
    },
  },
  mounted() {
    this.onMount();
  },
  methods: {
    filterWithoutAll() {
      if (this.outputValue === ".*" && this.hideAll) {
        return "";
      }
      return this.outputValue;
    },
    async saveFilter() {
      this.nodeFilterStore.saveFilter(this.project, {
        filterName: this.newFilterName,
        isJobEdit: true,
        filter: this.outputValue,
      });
      this.saveFilterModal = false;
      this.selectedFilterName = this.newFilterName;
      this.newFilterName = "";
      this.loadNodeFilters();
      this.$emit("filters-updated");
      this.eventBus.emit("nodefilter:savedFilters:changed");
    },
    async deleteFilterValue(filterName: string) {
      this.nodeFilterStore.removeFilter(this.project, filterName);
      this.loadNodeFilters();
      this.$emit("filters-updated");
      this.eventBus.emit("nodefilter:savedFilters:changed");
    },
    async deleteFilter() {
      this.nodeFilterStore.removeFilter(this.project, this.selectedFilterName);
      this.deleteFilterValue(this.selectedFilterName);
      this.deleteFilterModal = false;
      this.selectedFilterName = "";
      // this.loadNodeFilters()
      this.$emit("filters-updated");
    },
    async setDefaultFilterValue(filterName: string) {
      this.nodeFilterStore.setStoredDefaultFilter(this.project, filterName);
      this.nodeSummary.defaultFilter = filterName;
      this.eventBus.emit("nodefilter:savedFilters:changed");
    },
    async setDefaultFilter() {
      await this.setDefaultFilterValue(this.selectedFilterName);
    },
    async removeDefaultFilter() {
      this.nodeFilterStore.removeStoredDefaultFilter(this.project);
      this.nodeSummary.defaultFilter = null;
      this.eventBus.emit("nodefilter:savedFilters:changed");
    },
    doSearch() {
      if (
        this.selectedFilterName &&
        this.selectedFilterName !== this.matchedFilter
      ) {
        this.selectedFilterName = "";
      }
      this.$emit("update:modelValue", this.outputValue);
    },
    handleNodefilter(val: any) {
      if (val.filterName) {
        this.selectedFilterName = val.filterName;
      } else {
        this.selectedFilterName = "";
      }
      this.$emit("filter", val);
    },
    loadNodeFilters() {
      this.nodeSummary = this.nodeFilterStore.loadStoredProjectNodeFilters(
        this.project,
      );
    },
    handleDeleteSavedFilter(filterName: string) {
      this.selectedFilterName = filterName;
      this.deleteFilterModal = true;
    },
    handleSetDefaultAllFilter() {
      this.setDefaultFilterValue(".*");
    },
    async onMount() {
      if (this.modelValue) {
        this.outputValue = this.modelValue;
      }
      if (
        this.selectedFilterName &&
        this.selectedFilterName !== this.matchedFilter
      ) {
        this.selectedFilterName = "";
      }
      this.selectedFilterName = this.filterName;
      this.loadNodeFilters();
      this.eventBus.on(
        "nodefilter:action:deleteSavedFilter",
        this.handleDeleteSavedFilter,
      );
      this.eventBus.on(
        "nodefilter:action:setDefault",
        this.setDefaultFilterValue,
      );
      this.eventBus.on(
        "nodefilter:action:setDefaultAll",
        this.handleSetDefaultAllFilter,
      );
      this.eventBus.on(
        "nodefilter:action:removeDefault",
        this.removeDefaultFilter,
      );
    },
  },
});
</script>
