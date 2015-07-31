#!/usr/bin/env jython
"""
    Main Spark Library. Handles users, Machine Accounts, and Spark Rooms
    through REST calls.
    Requires simplejson library, included in the Python folder.

    Author: Tommy Romano (tomrom95)
"""
import urllib2, urllib
import simplejson as json
import warnings
warnings.filterwarnings('ignore')

class User(object):
    """ Spark user object. Requires Oauth2 token to use methods """

    def __init__(self,
                 token,
                 org_id,
                 host_name="conv-a.wbx2.com"):
        self.token = token
        self.user_url = "https://%s/conversation/api/v1/users" %host_name
        self.org_id = org_id
        self.user_id = self.get_user_id()

    def get_user_id(self):
        """ Gets machine account id """
        headers = {'Authorization': 'Bearer %s' % self.token,
                   'Content-type': 'application/json'}
        try:
            request = urllib2.Request(self.user_url, headers=headers)
            response = urllib2.urlopen(request)
            out = json.loads(response.read())
            return out['id']
        except Exception, ex:
            print "Error getting user id: %s" %ex

    def message_spark_room(self, room_id, message):
        """ Messages spark room, specified by room_id, as user """
        room = SparkRoom(room_id, self.org_id)
        return room.send_message_as_user(message, self.token)

    def add_user_to_spark_room(self, room_id, new_user):
        """ Adds other user to room, specified by room_id.
            other_user must be instance of User """
        room = SparkRoom(room_id, self.org_id)
        return room.add_to_room_by_id(self.user_id, new_user.user_id, self.token)

class MachineAccount(User):
    """ Spark Machine Account object, inherits from User class. Requires username
        and password, also org id if different from given """

    def __init__(self,
                 machine_user,
                 machine_pw,
                 org_id,
                 basic_auth,
                 id_broker="idbroker.webex.com/idb"):
        self.machine_user = machine_user
        self.machine_pw = machine_pw
        self.basic_auth = basic_auth
        self.saml_url = "https://" + id_broker + "/token/%s/v1/actions/GetBearerToken/invoke"
        self.oauth_url = "https://%s/oauth2/v1/access_token" %id_broker
        self.org_id = org_id
        self.token = self.renew_oauth_token()
        super(MachineAccount, self).__init__(self.token, self.org_id)

    def renew_oauth_token(self):
        """ Gets a new Oauth token for the machine user """
        return self.request_oauth_token(self.request_saml_assertion(
            self.machine_user, self.machine_pw))

    def request_saml_assertion(self, machine_user, machine_pw):
        """ Requests SAML assertion using machine account name/password """
        headers = {'Content-type': 'application/json'}
        data = json.dumps({
            "name": machine_user,
            "password": machine_pw
            })
        try:
            request = urllib2.Request(self.saml_url % self.org_id, data=data, headers=headers)
            response = urllib2.urlopen(request)
            out = json.loads(response.read())
            return out["BearerToken"]
        except Exception, ex:
            print "Error requesting SAML assertion: %s" %ex

    def request_oauth_token(self, assertion):
        """ Requests oauth2 token using SAML assertion """
        headers = {'Content-type': 'application/json',
                   'Authorization': 'Basic %s' %self.basic_auth,
                   'Cache-Control': 'no-cache',
                   'Content-type': 'application/x-www-form-urlencoded'
                   }
        data = urllib.urlencode({
            'grant_type': 'urn:ietf:params:oauth:grant-type:saml2-bearer',
            'assertion': assertion,
            'scope': 'webex-squared:get_conversation webex-squared:kms_read webex-squared:' + (
                'kms_write webex-squared:kms_bind Identity:SCIM')
        })
        try:
            request = urllib2.Request(self.oauth_url, data=data, headers=headers)
            response = urllib2.urlopen(request)
            out = json.loads(response.read())
            return out["access_token"]
        except Exception, ex:
            print "Error getting Oauth2 token: %s" %ex

class SparkRoom(object):
    """ Spark Room object, requires room_id for methods """

    def __init__(self,
                 room_id,
                 org_id,
                 host_name="conv-a.wbx2.com"):
        self.room_id = room_id
        self.org_id = org_id
        self.conv_url = "https://" + host_name + "/conversation/api/v1/conversations/%s"
        self.action_url = "https://%s/conversation/api/v1/activities" %host_name
        self.user_url = "https://%s/conversation/api/v1/users" %host_name

    def add_user_to_room(self, existing_user, new_user):
        """ Adds another user to spark room given instance
            of an existing user and a new user. Both users must be
            instances of User class. """
        user_id = existing_user.user_id
        new_id = new_user.user_id
        return self.add_to_room_by_id(user_id, new_id, existing_user.token)

    def add_to_room_by_id(self, user_id, new_id, token):
        """ Add account specified by new_id to spark room
            given existing user id """
        headers = {'Authorization': 'Bearer %s' % token,
                   'Content-type': 'application/json'}
        data = json.dumps({
            "actor": {
                "id": user_id,
                "objectType": "person"
            },
            "addParticipant": True,
            "object": {
                "id": new_id,
                "objectType": "person"
            },
            "objectType": "activity",
            "target": {
                "id": self.room_id,
                "objectType": "conversation",
                "url": self.conv_url %self.room_id
            },
            "verb": "add"
        })
        try:
            print 'Adding user to room "%s"\n.......' %(self.room_id)
            request = urllib2.Request(self.action_url, data=data, headers=headers)
            response = urllib2.urlopen(request)
            out = json.loads(response.read())
            if 'errorCode' in out:
                print out['message']
            else:
                print "User added."
            return True
        except Exception, ex:
            print "Error adding user: %s" %ex
            return False

    def send_message_as_user(self, message, token):
        """ Given message and oauth2 token, messages Spark room """
        headers = {'Authorization': 'Bearer %s' % token,
                   'Content-type': 'application/json'}
        data = json.dumps({
            "object": {
                "displayName": message,
                "objectType": "comment"
            },
            "objectType": "activity",
            "target": {
                "id": self.room_id,
                "objectType": "conversation",
                "url": self.conv_url % self.room_id
            },
            "verb": "post"
            })
        try:
            print 'Sending message to room "%s":\n=> "%s"\n.......' %(self.room_id, message)
            request = urllib2.Request(self.action_url, data=data, headers=headers)
            response = urllib2.urlopen(request)
            print "Message sent."
            return True
        except Exception, ex:
            print "Error messaging spark room: %s" %ex
            return False
