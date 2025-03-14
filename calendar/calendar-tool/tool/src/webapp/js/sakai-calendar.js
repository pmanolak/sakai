const sakaiCalendar = {
  calendar: {},
  userLanguage: window.top.portal.locale,
  userTimeZone: window.top.portal.user.timezone || 'local',

  // Initialize the calendar and attach it to the calendar div.
  initializeSakaiCalendar (calendarDiv, initialDate, initialView) {
    // Get the event color from the skin variables.
    const computedStyle = getComputedStyle(document.querySelector(':root'));
    const eventBackgroundColor = computedStyle.getPropertyValue('--infoBanner-bgcolor', '#e9f5fc');
    const eventTextColor = computedStyle.getPropertyValue('--infoBanner-color', '#196390');
    const sakaiOrigin = window.location.origin;
    const siteId = window.top.portal.siteId;
    // We need the updated calendar events, do not cache requests.
    const requestInit = {
      method: 'GET',
      headers: {
        'cache-control': 'no-cache'
      }
    };
    // Initialize fullcalendar and render it in the calendarDiv.
    this.calendar = new FullCalendar.Calendar(calendarDiv, {
      initialView: this.getDefaultSubview(initialView),
      initialDate: initialDate,
      timeZone: sakaiCalendar.userTimeZone,
      aspectRatio: 1.35,
      scrollTime: '06:00',
      scrollTimeReset: false,
      displayEventTime: false,
      allDaySlot: false,
      themeSystem: 'bootstrap5',
      headerToolbar: {
        left: 'prev,next today',
        center: 'title',
        right: 'dayGridMonth,timeGridWeek,timeGridDay,listWeek'
      },
      buttonIcons: {
        /*Use of bootstrap5 as themeSystem will expect bootstrap icons and prepend bi bi-*/
        prev: 'caret-left',
        next: 'caret-right',
      },
      eventSources: [{
        events: function(event, successCallback, failureCallback) {
          const requestStartDate = moment(event.start).format('YYYY-MM-DD');
          const requestEndDate = moment(event.end).format('YYYY-MM-DD');
          const requestData = {
            merged: true,
            firstDate: requestStartDate,
            lastDate: requestEndDate
          };
          // Fetch the site calendar events from the REST endpoint.
          const calendarRequestUrl = new URL(`${sakaiOrigin}/direct/calendar/site/${siteId}.json`);
          Object.keys(requestData).forEach( (key) => calendarRequestUrl.searchParams.append(key, requestData[key]));
          window.fetch(calendarRequestUrl, requestInit).then( (response) => {
           if (response.ok) {
             return response.json()
           } else throw new Error("Network error while fetching calendar data");
          }).then( (responseData) => {
            const events = [];
            responseData.calendar_collection.forEach( (event) => {
              // Every Sakai Calendar event has to be mapped with a full calendar event.
              // .tz() converts the event to the timezone of the user.
              const startDate = moment(new Date(event.firstTime.time)).tz(sakaiCalendar.userTimeZone).format();
              let endDate;
              if (event.lastTime) {
                // use lastTime if it is a detailed event
                endDate = moment(new Date(event.lastTime.time + 1000)).tz(sakaiCalendar.userTimeZone).format();
              } else {
                // otherwise calculate the endDate using the duration
                endDate = moment(new Date(event.firstTime.time + event.duration + 1000)).tz(sakaiCalendar.userTimeZone).format();
              }
              // The calendar event url needs to be a link to view or edit the event.
              const eventReference = event.eventReference;
              const eventLink = new URL(window.location);
              eventLink.searchParams.append("eventReference", eventReference);
              eventLink.searchParams.append("sakai_action", "doDescription");
              eventLink.searchParams.append("panel", "Main");
              events.push({
                url: eventLink.href,
                title: event.title,
                start: startDate,
                end: endDate,
                site_name: event.siteName,
                type: event.type,
                icon: event.eventIcon,
                id:  event.eventId,
                attachments: event.attachments,
                eventReference: event.eventReference
              });
            });
            successCallback(events);
          }).catch( (error) => console.error(error));
        }
      }],
      eventSourceSuccess: function(content, xhr) {
        return content.eventArray;
      },
      eventBackgroundColor: eventBackgroundColor,
      eventTextColor: eventTextColor,
      eventContent: function (args) {
        const isListView = sakaiCalendar.calendar.getCurrentData() && sakaiCalendar.calendar.getCurrentData().currentViewType.includes('list');
        // Create the default event structure to benefit from the theme.
        const eventTitle = document.createElement('div');
        eventTitle.classList.add(!isListView ? 'fc-event-title' : 'fc-list-event-title');
        // Build our event HTML including the default icon for the event type.
        const icon = args.event._def.extendedProps.icon;
        const title = args.event._def.title;
        const titleElement = document.createTextNode(title);
        const type = args.event._def.extendedProps.type;
        const eventUrl = args.event._def.url;
        const eventLink = document.createElement('a');
        eventLink.href = eventUrl;
        eventLink.title = title;
        eventLink.appendChild(titleElement);
        const iconSpan = document.createElement('span');
        iconSpan.classList.add('icon');
        iconSpan.classList.add(icon);
        iconSpan.title = type;
        // Append our custom event with icon to the default event structure.
        eventTitle.appendChild(iconSpan);
        eventTitle.appendChild(!isListView ? titleElement : eventLink);
        // Return the modified event including out event structure.
        return {
          html: eventTitle.outerHTML
        };
      }
    });

    // Set the user language to the fullcalendar UI.
    sakaiCalendar.userLanguage && this.calendar.setOption('locale', sakaiCalendar.userLanguage);
    this.calendar.render();
  },

  // Go to the current date set by the backend.
  gotoDate (currentDate) {
    this.calendar.gotoDate(currentDate);
  },
  
  setScrollTime (scrollTimeParam) {
	this.calendar.scrollToTime(scrollTimeParam);
  },
  
  setAspectRatio (aspectRatio) {
	this.calendar.setOption('aspectRatio', aspectRatio);
  },

    // When the user changes the view, reflect the change in a param to set the default view.
  onChangeCalendarView () {
    const currentView = this.calendar.currentData.currentViewType;
    const defaultViewParams = document.getElementsByName('calendar_default_subview');
    if (defaultViewParams && defaultViewParams.length > 0) {
      defaultViewParams[0].value = currentView;
    }
    // Reenable the button when the subview changes.
    const changeDefaultViewButton = document.getElementsByName('eventSubmit_doDefaultview');
    if (changeDefaultViewButton && changeDefaultViewButton.length > 0) {
      changeDefaultViewButton[0].removeAttribute('disabled');
    }
  },
  // This logic is associated to get the default subview value for day, month, week or list.
  getDefaultSubview (defaultSubview) {
    let view;
    switch (defaultSubview) {
      case 'day':
        view = 'timeGridDay';
        break;
      case 'month':
        view = 'dayGridMonth';
        break;
      case 'list':
        view = 'listWeek';
        break;
      case 'week':
      default:
        view = 'timeGridWeek';
    }

    document.querySelectorAll('.fc-timeGridWeek-button, .fc-dayGridMonth-button, .fc-timeGridDay-button, .fc-listWeek-button').forEach( (viewButton) => viewButton.setAttribute('onclick', 'sakaiCalendar.onChangeCalendarView();'));

    return view;
  },

  printCalendar (printableVersionUrl) {
    const currentSelectedDate = new Date(this.calendar.currentData.currentDate.getTime());
    const currentView = this.calendar.currentData.currentViewType.toLowerCase();

    /** Calendar Printing Views. */
    // LIST_SUBVIEW = 1;
    // MONTH_VIEW = 3;
    // List is the default subview for weekly, daily and list views.
    let currentPrintview = 1;
    // Different views have different time ranges
    if (currentView.includes('month')) {
      currentPrintview = 3;
    }

    // Now we have the right time ranges, we must replace the query params.
    const defaultPrintCalendarUrl = new URL(printableVersionUrl);
    const defaultPrintCalendarParams = defaultPrintCalendarUrl.searchParams;
    defaultPrintCalendarParams.forEach((value, key) => {
      switch(key) {
        case 'scheduleType':
          defaultPrintCalendarParams.set('scheduleType', currentPrintview);
          break;
        default:
      }
    });
    defaultPrintCalendarParams.set('selectedCalendarDate', currentSelectedDate.toISOString());
    // Now we have the right URL, make the print request.
    window.open(defaultPrintCalendarUrl.href, '_blank');
  }

};
