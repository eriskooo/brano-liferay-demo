package com.example.crm.frontend.base.session;

/** Odkiaľ fasáda číta: stará session alebo Redis. Zápis ide vždy do oboch. */
public enum ReadSource { PORTLET, REDIS }
