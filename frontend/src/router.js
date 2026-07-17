import { createRouter, createWebHistory } from 'vue-router';
import App from './App.vue';

const ReportResearchPage = () => import('./views/ReportResearchPage.vue');

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'workspace', component: App },
    { path: '/reports/:reportId(\\d+)', name: 'report-research', component: ReportResearchPage },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ],
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition;
    if (to.hash) return { el: to.hash, behavior: 'smooth' };
    return { top: 0 };
  }
});
