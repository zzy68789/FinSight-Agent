import { ref } from 'vue';
import {
  createAgentEventProjection,
  reduceAgentEvent,
  replayAgentEvents
} from '../modules/agentEventProjection';

/**
 * 集中维护实时 SSE 与历史 Trace 共用的 Agent 页面状态，页面组件只负责交互和渲染。
 */
export function useAgentRunProjection() {
  const agentPlan = ref(null);
  const agentBudget = ref(null);
  const agentEvents = ref([]);
  const agentProjection = ref(createAgentEventProjection());
  const latestStockTaskId = ref(null);
  const financialMetrics = ref([]);
  const financialEvidence = ref([]);
  const financialSnapshotSummary = ref(null);
  const financialRiskAssessment = ref(null);
  const financialCompliance = ref(null);
  const financialEvaluation = ref(null);
  const bullBearResearch = ref(null);
  const financialProviderStages = ref([]);
  const currentStep = ref('idle');
  const completedSteps = ref([]);
  const logs = ref([]);
  const displayedReport = ref('');
  const isTyping = ref(false);
  let typingInterval = null;

  const typeWriterEffect = (text) => {
    isTyping.value = true;
    if (typingInterval) clearInterval(typingInterval);
    let index = 0;
    typingInterval = setInterval(() => {
      if (index < text.length) {
        displayedReport.value += text.slice(index, index + 3);
        index += 3;
        return;
      }
      clearInterval(typingInterval);
      typingInterval = null;
      isTyping.value = false;
    }, 10);
  };

  const applyAgentProjection = (projection, animateReport = false) => {
    const nextReport = projection.finalReport || '';
    agentProjection.value = projection;
    currentStep.value = projection.currentStep;
    completedSteps.value = projection.completedSteps;
    agentEvents.value = projection.events;
    agentPlan.value = projection.plan;
    agentBudget.value = projection.budget;
    latestStockTaskId.value = projection.taskId || latestStockTaskId.value;
    financialMetrics.value = projection.metrics;
    financialEvidence.value = projection.evidence;
    financialSnapshotSummary.value = projection.snapshotSummary;
    financialRiskAssessment.value = projection.riskAssessment;
    financialCompliance.value = projection.compliance;
    financialEvaluation.value = projection.evaluation;
    bullBearResearch.value = projection.bullBearResearch;
    financialProviderStages.value = projection.providerStages;
    logs.value = projection.logs;
    if (animateReport && nextReport && nextReport !== displayedReport.value) {
      displayedReport.value = '';
      typeWriterEffect(nextReport);
    } else if (!animateReport && nextReport) {
      displayedReport.value = nextReport;
    }
  };

  const resetAgentRun = (initialLog = '') => {
    if (typingInterval) clearInterval(typingInterval);
    typingInterval = null;
    isTyping.value = false;
    displayedReport.value = '';
    latestStockTaskId.value = null;
    const projection = createAgentEventProjection();
    projection.currentStep = 'run_created';
    if (initialLog) projection.logs.push(initialLog);
    applyAgentProjection(projection);
  };

  const handleAgentEvent = (event) => {
    const base = { ...agentProjection.value, logs: [...logs.value] };
    applyAgentProjection(reduceAgentEvent(base, event), true);
  };

  const applyAgentTrace = (events, logMessage = '') => {
    const projection = replayAgentEvents(events);
    if (logMessage) projection.logs.push(logMessage);
    applyAgentProjection(projection, false);
  };

  const pushAgentLog = (message) => logs.value.push(message);
  const markAgentRunDone = () => {
    currentStep.value = 'done';
  };

  return {
    agentPlan,
    agentBudget,
    agentEvents,
    latestStockTaskId,
    financialMetrics,
    financialEvidence,
    financialSnapshotSummary,
    financialRiskAssessment,
    financialCompliance,
    financialEvaluation,
    bullBearResearch,
    financialProviderStages,
    currentStep,
    completedSteps,
    logs,
    displayedReport,
    isTyping,
    resetAgentRun,
    handleAgentEvent,
    applyAgentTrace,
    pushAgentLog,
    markAgentRunDone
  };
}
